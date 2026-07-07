package io.taanielo.jmud;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the dependency and layering rules described in {@code AGENTS.md} §3 so that
 * architectural drift (e.g. the composition root leaking into the socket adapter, or
 * {@code SocketClient} growing unrelated responsibilities) fails the build instead of being
 * caught in code review.
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
     * stay reachable only from the composition root ({@code bootstrap}, AGENTS.md §3.3) and
     * {@code Main}. Nothing else — domain services, other adapters — may depend on them
     * directly, so that transport can be swapped or extended without rippling through the
     * codebase.
     */
    @ArchTest
    static final ArchRule transport_isolation =
            noClasses()
                    .that()
                    .resideOutsideOfPackages("..core.server..", "..bootstrap..")
                    .and(DescribedPredicate.not(JavaClass.Predicates.equivalentTo(Main.class)))
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..core.server.socket..", "..core.server.ssh..")
                    .because("transport adapters (socket/ssh) are infrastructure and must only be wired from "
                            + "the composition root and Main (AGENTS.md §3.2, §3.3)");

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
     * No legacy resurrection: {@code io.taanielo.jmud.command} and {@code core.command} were the
     * dead, duplicate command systems removed in issue #178 (AGENTS.md §3.3). Since the packages
     * no longer exist, this rule is trivially satisfied and simply guards against either package
     * being reintroduced.
     */
    @ArchTest
    static final ArchRule no_legacy_resurrection =
            noClasses()
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("io.taanielo.jmud.command..", "io.taanielo.jmud.core.command..")
                    .because("io.taanielo.jmud.command.. and core.command.. were deleted as dead code "
                            + "(AGENTS.md §3.3, issue #178); they must not be reintroduced");

    /**
     * SocketClient thin-transport guard: {@code SocketClient} is a pure transport adapter
     * (AGENTS.md §3.3, issue #182) and must not depend on {@code GameActionService}. All game
     * logic must live in {@code SocketCommandContextImpl} or domain/application services.
     *
     * <p>If this rule fires, you are adding game logic to the transport layer — move it to
     * {@code SocketCommandContextImpl} or a domain service instead.
     */
    @ArchTest
    static final ArchRule socket_client_no_game_action_dependency =
            noClasses()
                    .that().haveSimpleName("SocketClient")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName("GameActionService")
                    .because("SocketClient is a thin transport adapter; game logic belongs in "
                            + "SocketCommandContextImpl (AGENTS.md §3.3, issue #182)");
}
