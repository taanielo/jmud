/**
 * The composition root: the only place allowed to construct {@code Json*} repository
 * implementations and infrastructure schedulers (AGENTS.md §3.3), wiring the domain services
 * used by every transport adapter. NullAway-checked ({@code @NullMarked}) since this is a new
 * package.
 */
@NullMarked
package io.taanielo.jmud.bootstrap;

import org.jspecify.annotations.NullMarked;
