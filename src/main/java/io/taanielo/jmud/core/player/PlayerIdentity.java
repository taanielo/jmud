package io.taanielo.jmud.core.player;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.RaceId;

public class PlayerIdentity {
    /** Maximum length of a player's custom LOOK description, in characters. */
    public static final int MAX_DESCRIPTION_LENGTH = 240;

    private final User user;
    private final int level;
    private final long experience;
    private final RaceId race;
    private final ClassId classId;
    /** Custom roleplay LOOK description; never null, empty string means "not set". */
    private final String description;
    /**
     * Username of the player's spouse (see the MARRY command), or {@code null} when unmarried. Stored
     * as the spouse's {@link io.taanielo.jmud.core.authentication.Username} display value; matching is
     * case-insensitive via {@code Username}. Blank strings normalise to {@code null}.
     */
    private final @Nullable String spouse;
    /**
     * Id (value form) of the room the player has anchored their recall/respawn point to via the BIND
     * command, or {@code null} when they have never bound (in which case recall/respawn fall back to
     * the world's default starting room, Greystone Town). Blank strings normalise to {@code null}.
     */
    private final @Nullable String boundRoomId;
    /**
     * Username of this player's mentor (see the MENTOR command) when this player is the mentee, or
     * {@code null} when they have no mentor. Mutually exclusive with {@link #mentee}: a player is at
     * most one side of a single mentor bond. Blank strings normalise to {@code null}.
     */
    private final @Nullable String mentor;
    /**
     * Username of this player's mentee (see the MENTOR command) when this player is the mentor, or
     * {@code null} when they have no mentee. Blank strings normalise to {@code null}.
     */
    private final @Nullable String mentee;
    /** Epoch-millis timestamp the current mentor bond was formed, or {@code 0} when there is no bond. */
    private final long mentorBondSince;
    /** Lifetime count of mentees this player has guided to graduation; never negative, defaults to 0. */
    private final int menteesGraduated;

    public PlayerIdentity(User user, int level, long experience, RaceId race, ClassId classId) {
        this(user, level, experience, race, classId, "");
    }

    public PlayerIdentity(User user, int level, long experience, RaceId race, ClassId classId, String description) {
        this(user, level, experience, race, classId, description, null);
    }

    public PlayerIdentity(
        User user,
        int level,
        long experience,
        RaceId race,
        ClassId classId,
        String description,
        @Nullable String spouse
    ) {
        this(user, level, experience, race, classId, description, spouse, null);
    }

    public PlayerIdentity(
        User user,
        int level,
        long experience,
        RaceId race,
        ClassId classId,
        String description,
        @Nullable String spouse,
        @Nullable String boundRoomId
    ) {
        this(user, level, experience, race, classId, description, spouse, boundRoomId, null, null, 0L, 0);
    }

    public PlayerIdentity(
        User user,
        int level,
        long experience,
        RaceId race,
        ClassId classId,
        String description,
        @Nullable String spouse,
        @Nullable String boundRoomId,
        @Nullable String mentor,
        @Nullable String mentee,
        long mentorBondSince,
        int menteesGraduated
    ) {
        this.user = Objects.requireNonNull(user, "User is required");
        this.level = level;
        this.experience = experience;
        this.race = Objects.requireNonNullElse(race, RaceId.of("human"));
        this.classId = Objects.requireNonNullElse(classId, ClassId.of("adventurer"));
        this.description = Objects.requireNonNullElse(description, "").trim();
        this.spouse = spouse == null || spouse.isBlank() ? null : spouse.trim();
        this.boundRoomId = boundRoomId == null || boundRoomId.isBlank() ? null : boundRoomId.trim();
        this.mentor = mentor == null || mentor.isBlank() ? null : mentor.trim();
        this.mentee = mentee == null || mentee.isBlank() ? null : mentee.trim();
        this.mentorBondSince = Math.max(0L, mentorBondSince);
        this.menteesGraduated = Math.max(0, menteesGraduated);
    }

    public User user() {
        return user;
    }

    public int level() {
        return level;
    }

    public long experience() {
        return experience;
    }

    public RaceId race() {
        return race;
    }

    public ClassId classId() {
        return classId;
    }

    /**
     * Returns the player's custom LOOK description, or an empty string when none is set.
     */
    public String description() {
        return description;
    }

    /**
     * Returns the username of this player's spouse (see the MARRY command), or {@code null} when the
     * player is unmarried.
     */
    public @Nullable String spouse() {
        return spouse;
    }

    /**
     * Returns the id (value form) of the room this player has bound their recall/respawn point to via
     * the BIND command, or {@code null} when they have never bound (recall/respawn then default to the
     * world's starting room).
     */
    public @Nullable String boundRoomId() {
        return boundRoomId;
    }

    /**
     * Returns the username of this player's mentor (see the MENTOR command) when this player is the
     * mentee, or {@code null} when they have no mentor.
     */
    public @Nullable String mentor() {
        return mentor;
    }

    /**
     * Returns the username of this player's mentee (see the MENTOR command) when this player is the
     * mentor, or {@code null} when they have no mentee.
     */
    public @Nullable String mentee() {
        return mentee;
    }

    /**
     * Returns the epoch-millis timestamp the current mentor bond was formed, or {@code 0} when there
     * is no active bond.
     */
    public long mentorBondSince() {
        return mentorBondSince;
    }

    /**
     * Returns the lifetime count of mentees this player has guided to graduation (see the MENTOR
     * command); never negative, {@code 0} for a player who has never mentored anyone to graduation.
     */
    public int menteesGraduated() {
        return menteesGraduated;
    }

    public PlayerIdentity withLevel(int nextLevel) {
        return new PlayerIdentity(user, nextLevel, experience, race, classId, description, spouse, boundRoomId,
            mentor, mentee, mentorBondSince, menteesGraduated);
    }

    public PlayerIdentity withExperience(long nextExperience) {
        return new PlayerIdentity(user, level, nextExperience, race, classId, description, spouse, boundRoomId,
            mentor, mentee, mentorBondSince, menteesGraduated);
    }

    public PlayerIdentity withRace(RaceId nextRace) {
        return new PlayerIdentity(user, level, experience, nextRace, classId, description, spouse, boundRoomId,
            mentor, mentee, mentorBondSince, menteesGraduated);
    }

    public PlayerIdentity withClassId(ClassId nextClassId) {
        return new PlayerIdentity(user, level, experience, race, nextClassId, description, spouse, boundRoomId,
            mentor, mentee, mentorBondSince, menteesGraduated);
    }

    /**
     * Returns a copy of this identity with the given custom LOOK description, trimmed; pass an empty
     * or {@code null} string to clear it back to the default generated line.
     *
     * @param newDescription the new description text, or {@code null}/blank to clear
     */
    public PlayerIdentity withDescription(String newDescription) {
        return new PlayerIdentity(user, level, experience, race, classId, newDescription, spouse, boundRoomId,
            mentor, mentee, mentorBondSince, menteesGraduated);
    }

    /**
     * Returns a copy of this identity bonded to the given spouse, or unmarried when {@code newSpouse}
     * is {@code null}/blank.
     *
     * @param newSpouse the spouse's username display value, or {@code null}/blank to clear the bond
     */
    public PlayerIdentity withSpouse(@Nullable String newSpouse) {
        return new PlayerIdentity(user, level, experience, race, classId, description, newSpouse, boundRoomId,
            mentor, mentee, mentorBondSince, menteesGraduated);
    }

    /**
     * Returns a copy of this identity anchored to the given recall/respawn room, or unbound when
     * {@code newBoundRoomId} is {@code null}/blank (recall/respawn then default to the starting room).
     *
     * @param newBoundRoomId the bound room id value, or {@code null}/blank to clear the anchor
     */
    public PlayerIdentity withBoundRoomId(@Nullable String newBoundRoomId) {
        return new PlayerIdentity(user, level, experience, race, classId, description, spouse, newBoundRoomId,
            mentor, mentee, mentorBondSince, menteesGraduated);
    }

    /**
     * Returns a copy of this identity holding the given mentor bond. Exactly one of {@code newMentor}
     * (this player is the mentee) or {@code newMentee} (this player is the mentor) should be non-blank;
     * pass both {@code null}/blank with {@code since} 0 to clear the bond entirely.
     *
     * @param newMentor    the mentor's username when this player is the mentee, or {@code null}/blank
     * @param newMentee    the mentee's username when this player is the mentor, or {@code null}/blank
     * @param since        the epoch-millis timestamp the bond formed, or {@code 0} when clearing it
     */
    public PlayerIdentity withMentorBond(@Nullable String newMentor, @Nullable String newMentee, long since) {
        return new PlayerIdentity(user, level, experience, race, classId, description, spouse, boundRoomId,
            newMentor, newMentee, since, menteesGraduated);
    }

    /**
     * Returns a copy of this identity with the lifetime graduated-mentee counter replaced.
     *
     * @param nextCount the new lifetime graduation count; negatives clamp to 0
     */
    public PlayerIdentity withMenteesGraduated(int nextCount) {
        return new PlayerIdentity(user, level, experience, race, classId, description, spouse, boundRoomId,
            mentor, mentee, mentorBondSince, nextCount);
    }
}
