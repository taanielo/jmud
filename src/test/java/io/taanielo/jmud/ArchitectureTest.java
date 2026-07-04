package io.taanielo.jmud;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

/**
 * Enforces the dependency and layering rules described in {@code AGENTS.md} §3 so that
 * architectural drift (e.g. the composition root leaking into the socket adapter, or
 * {@code SocketClient} growing unrelated responsibilities) fails the build instead of being
 * caught in code review. Rules that the existing codebase already violates are wrapped in
 * {@link FreezingArchRule} so the recorded debt can only shrink, never grow, going forward.
 *
 * <p>The frozen violation store lives at {@code src/test/resources/archunit_store} and is
 * committed to version control; see {@code src/test/resources/archunit.properties} for the
 * store configuration.
 */
@AnalyzeClasses(packages = "io.taanielo.jmud")
class ArchitectureTest {

    /**
     * Test classes are named consistently across the codebase (see AGENTS.md §10), so a
     * simple-name suffix is a reliable, dependency-free way to recognize test code from within
     * an ArchUnit rule without needing a separate test-only analysis pass.
     */
    private static final DescribedPredicate<JavaClass> TEST_CODE =
            new DescribedPredicate<>("is test code") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return javaClass.getSimpleName().endsWith("Test") || javaClass.getSimpleName().endsWith("Tests");
                }
            };

    /**
     * Transport isolation: the socket/SSH adapters are infrastructure (AGENTS.md §3.2) and must
     * stay reachable only from the composition root (currently inside {@code core.server}) and
     * {@code Main}. Nothing else — domain services, other adapters — may depend on them
     * directly, so that transport can be swapped or extended without rippling through the
     * codebase.
     */
    @ArchTest
    static final ArchRule transport_isolation =
            noClasses()
                    .that()
                    .resideOutsideOfPackages("..core.server..")
                    .and(DescribedPredicate.not(JavaClass.Predicates.equivalentTo(Main.class)))
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..core.server.socket..", "..core.server.ssh..")
                    .because("transport adapters (socket/ssh) are infrastructure and must only be wired from "
                            + "the composition root and Main (AGENTS.md §3.2, §3.3)");

    /**
     * Jackson containment: JSON (de)serialization is an infrastructure/adapter concern and must
     * stay confined to the DTO and JSON-repository edges (plus the messaging DTOs), so domain
     * code stays free of framework annotations (AGENTS.md §3.2). This is frozen because several
     * value objects (e.g. {@code Player}) currently carry Jackson annotations directly.
     */
    @ArchTest
    static final ArchRule jackson_containment =
            FreezingArchRule.freeze(
                    noClasses()
                            .that()
                            .resideOutsideOfPackages("..dto..", "..repository.json..", "..messaging..")
                            .should()
                            .dependOnClassesThat()
                            .resideInAPackage("com.fasterxml.jackson..")
                            .because("Jackson is a JSON infrastructure concern; it must only be used at the "
                                    + "dto/repository.json/messaging edges, never in domain code (AGENTS.md §3.2)"));

    /**
     * Repository construction: {@code Json*Repository} implementations may only be instantiated
     * (or otherwise accessed) from {@code GameContext}, the composition root (AGENTS.md §3.3),
     * or test code. Everywhere else must depend on the repository interface, injected via the
     * constructor.
     */
    private static final DescribedPredicate<JavaClass> JSON_REPOSITORY_IMPLEMENTATION =
            DescribedPredicate.<JavaClass>describe(
                    "is a Json*Repository implementation",
                    javaClass ->
                            javaClass.getSimpleName().startsWith("Json")
                                    && javaClass.getSimpleName().endsWith("Repository"));

    @ArchTest
    static final ArchRule repository_construction =
            classes()
                    .that(JSON_REPOSITORY_IMPLEMENTATION)
                    .should()
                    .onlyBeAccessed()
                    .byClassesThat(
                            DescribedPredicate.<JavaClass>describe(
                                            "is GameContext",
                                            javaClass -> javaClass.getSimpleName().equals("GameContext"))
                                    // a repository's own constructors/methods calling each other (e.g. a
                                    // no-arg constructor delegating to the Path-taking one) is not an
                                    // external "construction site" and must not count as a violation.
                                    .or(JSON_REPOSITORY_IMPLEMENTATION)
                                    .or(TEST_CODE))
                    .because("only the composition root (GameContext) may construct concrete Json*Repository "
                            + "implementations; everywhere else must depend on the repository interface "
                            + "(AGENTS.md §3.3)");

    /**
     * Domain purity: these packages hold core game rules and must stay free of networking and
     * transport-layer dependencies (AGENTS.md §3.1, §3.2), so combat/effects/ability/quest/shop/
     * bank/party logic remains unit-testable without sockets (AGENTS.md §10).
     */
    @ArchTest
    static final ArchRule domain_purity =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            "..core.combat",
                            "..core.effects",
                            "..core.ability",
                            "..core.quest",
                            "..core.shop",
                            "..core.bank",
                            "..core.party")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("java.net..", "..core.server..")
                    .because("domain rule packages must not depend on networking or the server/transport "
                            + "layer (AGENTS.md §3.1, §5, §10)");

    /**
     * No legacy resurrection: {@code io.taanielo.jmud.command} and {@code core.command} are dead
     * code scheduled for deletion (AGENTS.md §3.3) — nothing new may depend on them, so they
     * cannot silently regain callers while awaiting removal. Frozen because at least one live
     * class ({@code core.server.socket.QuitCommand}) currently still reaches into the legacy
     * {@code command} package, along with the legacy packages' own internal dependencies.
     */
    @ArchTest
    static final ArchRule no_legacy_resurrection =
            FreezingArchRule.freeze(
                    noClasses()
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage("io.taanielo.jmud.command..", "io.taanielo.jmud.core.command..")
                            .because("io.taanielo.jmud.command.. and core.command.. are dead code scheduled for "
                                    + "deletion; no new dependencies on them are allowed (AGENTS.md §3.3)"));
}
