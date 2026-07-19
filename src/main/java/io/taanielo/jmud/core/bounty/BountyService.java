package io.taanielo.jmud.core.bounty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.mob.MobTemplateRepository;
import io.taanielo.jmud.core.player.MailResult;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerMailService;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Domain service governing player-funded mob bounties.
 *
 * <p>Modelled on {@code AuctionService} for its persisted-ledger shape and on
 * {@code io.taanielo.jmud.core.guild.GuildQuestService} for the "credit on kill" hook: a player
 * {@link #post posts} gold against a mob type, and the next kill of that type anywhere
 * ({@link #claim}) pays every open backer's pooled stake to the killer, announces the payout
 * server-wide, and closes the paid entries.
 *
 * <p>All operations are pure with respect to the invoking {@link Player}: the player passed in is
 * never mutated in place; on success callers receive an updated player in the returned
 * {@link BountyResult}. Escrowed gold lives only in the {@link Bounty} record — no {@code Player}
 * field tracks it — and gold is conserved: a post debits exactly the stake, a cancel refunds exactly
 * the stake, and a payout hands the killer's party exactly the pooled total (see {@link #claim}).
 * Every method runs on the tick thread (AGENTS.md §5).
 */
@Slf4j
public class BountyService {

    private static final String NOTIFIER = "Bounty Board";

    private final BountyRepository bountyRepository;
    private final MobTemplateRepository mobTemplateRepository;
    private final MessageBroadcaster messageBroadcaster;
    private final PlayerMailService mailService;
    private final int maxOpenPerPlayer;

    /**
     * Creates a bounty service.
     *
     * @param bountyRepository      store of open bounties (in-memory snapshot + write-behind persistence)
     * @param mobTemplateRepository source of mob templates, used to resolve a POST target and confirm it
     *                              is killable (has a non-null attack id)
     * @param messageBroadcaster    sanctioned fan-out used to announce a payout server-wide
     * @param maxOpenPerPlayer      the maximum number of concurrent open bounties a single poster may
     *                              hold; must be positive
     */
    public BountyService(
        BountyRepository bountyRepository,
        MobTemplateRepository mobTemplateRepository,
        MessageBroadcaster messageBroadcaster,
        int maxOpenPerPlayer
    ) {
        this.bountyRepository = Objects.requireNonNull(bountyRepository, "bountyRepository is required");
        this.mobTemplateRepository =
            Objects.requireNonNull(mobTemplateRepository, "mobTemplateRepository is required");
        this.messageBroadcaster = Objects.requireNonNull(messageBroadcaster, "messageBroadcaster is required");
        if (maxOpenPerPlayer <= 0) {
            throw new IllegalArgumentException("maxOpenPerPlayer must be positive");
        }
        this.maxOpenPerPlayer = maxOpenPerPlayer;
        this.mailService = new PlayerMailService();
    }

    /**
     * Posts a bounty: escrows {@code gold} from the poster's on-hand balance against the resolved mob
     * type. Fails without any state change when the amount is not positive, the mob cannot be resolved,
     * the mob is a non-combatant (no attack id), the poster cannot afford the stake, the poster already
     * has an open bounty on that mob type, or the poster already holds the maximum number of concurrent
     * open bounties (see the {@code maxOpenPerPlayer} constructor argument).
     *
     * @param poster      the posting player
     * @param mobInput    the mob name or keyword to target
     * @param gold        the gold stake; must be positive and affordable
     * @param currentTick the tick the bounty is posted (used to report its age)
     * @return the result; on success the poster with the stake debited
     */
    public BountyResult post(Player poster, String mobInput, int gold, long currentTick) {
        Objects.requireNonNull(poster, "poster is required");
        if (gold <= 0) {
            return BountyResult.failure("You must stake a positive amount of gold.");
        }
        String normalized = mobInput == null ? "" : mobInput.trim();
        if (normalized.isEmpty()) {
            return BountyResult.failure("Post a bounty on what? Usage: BOUNTY POST <mob> <gold>");
        }
        @Nullable MobTemplate template = resolveTemplate(normalized).orElse(null);
        if (template == null) {
            return BountyResult.failure("There is no such creature to place a bounty on.");
        }
        if (template.attackId() == null) {
            return BountyResult.failure(
                "The " + template.name() + " is harmless; there is no glory in a bounty on it.");
        }
        if (poster.getGold() < gold) {
            return BountyResult.failure(
                "You cannot afford that. It costs " + gold + " gold and you have " + poster.getGold() + ".");
        }
        String templateId = template.id().getValue();
        List<Bounty> all = new ArrayList<>(bountyRepository.findAll());
        int openForPoster = 0;
        for (Bounty existing : all) {
            if (!existing.backer().equals(poster.getUsername())) {
                continue;
            }
            openForPoster++;
            if (existing.mobTemplateId().equals(templateId)) {
                return BountyResult.failure(
                    "You already have a bounty on the " + template.name()
                        + ". Cancel it first to change your stake.");
            }
        }
        if (openForPoster >= maxOpenPerPlayer) {
            return BountyResult.failure(
                "You already have the maximum of " + maxOpenPerPlayer
                    + " open bounties. Use BOUNTY CANCEL <mob> to free one up first.");
        }
        all.add(new Bounty(poster.getUsername(), templateId, template.name(), gold, currentTick));
        bountyRepository.save(all);
        Player updated = poster.addGold(-gold);
        return BountyResult.success(
            "You post a bounty of " + gold + " gold on the " + template.name() + ".", updated);
    }

    /**
     * Cancels the poster's own open bounty on the mob type matching {@code mobInput} and refunds the
     * staked gold in full. Fails without any state change when the poster has no open bounty matching
     * the input.
     *
     * @param poster   the cancelling player
     * @param mobInput the mob name or keyword identifying the poster's bounty to pull
     * @return the result; on success the poster with the stake refunded
     */
    public BountyResult cancel(Player poster, String mobInput) {
        Objects.requireNonNull(poster, "poster is required");
        String normalized = mobInput == null ? "" : mobInput.trim();
        if (normalized.isEmpty()) {
            return BountyResult.failure("Cancel a bounty on what? Usage: BOUNTY CANCEL <mob>");
        }
        String query = normalized.toLowerCase(Locale.ROOT);
        List<Bounty> all = new ArrayList<>(bountyRepository.findAll());
        @Nullable Bounty own = null;
        for (Bounty bounty : all) {
            if (!bounty.backer().equals(poster.getUsername())) {
                continue;
            }
            if (matches(bounty, query)) {
                own = bounty;
                break;
            }
        }
        if (own == null) {
            return BountyResult.failure("You have no bounty posted on that creature.");
        }
        all.remove(own);
        bountyRepository.save(all);
        Player updated = poster.addGold(own.reward());
        return BountyResult.success(
            "You cancel your bounty on the " + own.mobName() + " and reclaim " + own.reward() + " gold.",
            updated);
    }

    /**
     * Returns a server-wide summary of every open bounty, one row per mob type, with the pooled reward,
     * backer count, the age of the oldest open stake, and the ticks remaining before the soonest stake
     * on that type lapses (the oldest stake expires first). Ordered by pooled reward descending, then by
     * mob name.
     *
     * @param currentTick the current game tick, used to compute each row's age and remaining time
     * @param expiryTicks the configured bounty lifespan in ticks, used to compute remaining time
     * @return the open-bounty summaries (may be empty)
     */
    public List<BountyListing> listings(long currentTick, long expiryTicks) {
        Map<String, List<Bounty>> byMob = new LinkedHashMap<>();
        for (Bounty bounty : bountyRepository.findAll()) {
            byMob.computeIfAbsent(bounty.mobTemplateId(), k -> new ArrayList<>()).add(bounty);
        }
        List<BountyListing> listings = new ArrayList<>(byMob.size());
        for (List<Bounty> group : byMob.values()) {
            int total = 0;
            long oldest = Long.MAX_VALUE;
            String mobName = group.get(0).mobName();
            for (Bounty bounty : group) {
                total += bounty.reward();
                oldest = Math.min(oldest, bounty.postedTick());
            }
            long age = Math.max(0, currentTick - oldest);
            long remaining = Math.max(0, oldest + expiryTicks - currentTick);
            listings.add(new BountyListing(mobName, total, group.size(), age, remaining));
        }
        listings.sort(Comparator.comparingInt(BountyListing::totalReward).reversed()
            .thenComparing(BountyListing::mobName));
        return List.copyOf(listings);
    }

    /**
     * Removes every bounty that has gone unclaimed past {@code expiryTicks}, persists the remainder, and
     * returns the removed bounties so the caller can refund each poster their full stake. Called only
     * from the tick thread by {@link BountyExpiryTicker}. Mirrors {@code AuctionService#expireListings}:
     * if persistence of the remainder fails the whole batch is reported as not-yet-expired so no escrow
     * is lost, and it retries next tick.
     *
     * @param currentTick the current game tick
     * @param expiryTicks the configured bounty lifespan in ticks; must be positive
     * @return the bounties that expired on or before this tick (may be empty)
     */
    public List<Bounty> expireBounties(long currentTick, long expiryTicks) {
        if (expiryTicks <= 0) {
            throw new IllegalArgumentException("expiryTicks must be positive");
        }
        List<Bounty> all = bountyRepository.findAll();
        List<Bounty> remaining = new ArrayList<>(all.size());
        List<Bounty> expired = new ArrayList<>();
        for (Bounty bounty : all) {
            if (bounty.isExpired(currentTick, expiryTicks)) {
                expired.add(bounty);
            } else {
                remaining.add(bounty);
            }
        }
        if (expired.isEmpty()) {
            return List.of();
        }
        bountyRepository.save(remaining);
        return List.copyOf(expired);
    }

    /**
     * Refunds an expired bounty's full stake to its poster and mails them a note so they learn of the
     * refund without having to notice its absence from {@code BOUNTY LIST}. Pure — returns the updated
     * poster; the caller persists it wherever the poster is (live session or on disk), mirroring how
     * {@code AuctionService#applyExpiredReturn} returns an item to an online-or-offline seller.
     *
     * @param poster      the poster to refund (online or freshly loaded from persistence)
     * @param bounty      the expired bounty whose stake is refunded
     * @param currentTick the current game tick, used to time-stamp the mail
     * @return the poster with the stake refunded and an expiry note appended (mail is skipped silently
     *         when the mailbox is full)
     */
    public Player applyExpiredRefund(Player poster, Bounty bounty, long currentTick) {
        Objects.requireNonNull(poster, "poster is required");
        Objects.requireNonNull(bounty, "bounty is required");
        Player refunded = poster.addGold(bounty.reward());
        String body = "Your bounty of " + bounty.reward() + " gold on the " + bounty.mobName()
            + " expired unclaimed and was refunded to you.";
        MailResult result = mailService.send(refunded, NOTIFIER, currentTick, body);
        return result.success() && result.updatedPlayer() != null ? result.updatedPlayer() : refunded;
    }

    /**
     * Claims and closes every open bounty on the given mob type, returning the pooled total the killer
     * (and their party) has earned. When at least one bounty is claimed a server-wide announcement names
     * the killer, the mob, and the total. Called on the tick thread from the mob-death reward path,
     * alongside the personal quest-kill and guild-quest credit; the actual gold split across the killer's
     * party is applied by the caller (mirroring how a mob's gold drop is split), so gold is conserved.
     *
     * <p>A no-op returning {@code 0} when no bounty targets this mob type — including the common case of
     * an ordinary, un-bountied kill — so it never mutates state or announces on a normal death.
     *
     * @param mobTemplateId the template id of the mob that was just killed
     * @param killer        the player who landed the killing blow (named in the announcement)
     * @param mobName       the mob's display name (used in the announcement)
     * @return the pooled gold reward across all claimed bounties, or {@code 0} when there were none
     */
    public int claim(String mobTemplateId, Username killer, String mobName) {
        Objects.requireNonNull(mobTemplateId, "mobTemplateId is required");
        Objects.requireNonNull(killer, "killer is required");
        Objects.requireNonNull(mobName, "mobName is required");
        List<Bounty> all = bountyRepository.findAll();
        List<Bounty> remaining = new ArrayList<>(all.size());
        int total = 0;
        for (Bounty bounty : all) {
            if (bounty.mobTemplateId().equals(mobTemplateId)) {
                total += bounty.reward();
            } else {
                remaining.add(bounty);
            }
        }
        if (total <= 0) {
            return 0;
        }
        bountyRepository.save(remaining);
        messageBroadcaster.broadcastGlobal(new PlainTextMessage(
            "[Bounty] " + killer.getValue() + " has slain the " + mobName
                + " and claimed the bounty of " + total + " gold!"), Set.of());
        return total;
    }

    // ── internals ─────────────────────────────────────────────────────

    private Optional<MobTemplate> resolveTemplate(String input) {
        List<MobTemplate> templates;
        try {
            templates = mobTemplateRepository.findAll();
        } catch (RepositoryException e) {
            log.warn("Failed to read mob templates for bounty resolution: {}", e.getMessage());
            return Optional.empty();
        }
        String query = input.toLowerCase(Locale.ROOT);
        @Nullable MobTemplate exact = null;
        @Nullable MobTemplate partial = null;
        for (MobTemplate template : templates) {
            String name = template.name().toLowerCase(Locale.ROOT);
            String id = template.id().getValue().toLowerCase(Locale.ROOT);
            if (name.equals(query) || id.equals(query)) {
                exact = template;
                break;
            }
            if (partial == null && (name.contains(query) || id.contains(query))) {
                partial = template;
            }
        }
        return Optional.ofNullable(exact != null ? exact : partial);
    }

    private boolean matches(Bounty bounty, String query) {
        return bounty.mobName().toLowerCase(Locale.ROOT).contains(query)
            || bounty.mobTemplateId().toLowerCase(Locale.ROOT).contains(query);
    }
}
