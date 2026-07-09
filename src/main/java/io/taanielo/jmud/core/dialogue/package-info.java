/**
 * NPC dialogue domain: immutable dialogue trees ({@link io.taanielo.jmud.core.dialogue.DialogueTree})
 * of numbered response nodes that drive the {@code TALK}/{@code RESPOND} conversation flow, plus the
 * stateless {@link io.taanielo.jmud.core.dialogue.DialogueService} that renders nodes and resolves
 * player responses. Trees are pre-loaded from JSON via
 * {@link io.taanielo.jmud.core.dialogue.DialogueRepository}; per-player conversation position is held
 * by the session, not here. NullAway-checked ({@code @NullMarked}) since this is a new package.
 */
@NullMarked
package io.taanielo.jmud.core.dialogue;

import org.jspecify.annotations.NullMarked;
