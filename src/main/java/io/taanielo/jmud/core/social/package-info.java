/**
 * Player-to-player social bonds that are not combat or economy features.
 *
 * <p>Currently holds the {@link io.taanielo.jmud.core.social.MarriageService marriage} system: a
 * purely-opt-in, mechanically-inert roleplay bond between two players (see the MARRY command). The
 * transient proposal registry is tick-thread-owned, mirroring
 * {@link io.taanielo.jmud.core.player.DuelService}; the persisted bond itself lives on
 * {@link io.taanielo.jmud.core.player.Player}.
 */
@NullMarked
package io.taanielo.jmud.core.social;

import org.jspecify.annotations.NullMarked;
