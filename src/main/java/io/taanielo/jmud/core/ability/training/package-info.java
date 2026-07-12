/**
 * Domain services governing how players learn new abilities at the Master Trainer.
 *
 * <p>The training rules — trainable-pool membership, the ability level gate, the
 * already-learned check and practice-point spend — live here as pure, network-free
 * logic so they can be unit-tested without sockets (AGENTS.md §10). The socket layer
 * translates the {@link io.taanielo.jmud.core.ability.training.TrainingAttempt}
 * outcomes into player-facing messages and persistence.
 */
@NullMarked
package io.taanielo.jmud.core.ability.training;

import org.jspecify.annotations.NullMarked;
