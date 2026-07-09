/**
 * JSON-backed persistence for ferry definitions ({@code data/ferries/*.json}).
 *
 * <p>{@link io.taanielo.jmud.core.transport.repository.json.JsonFerryRepository} loads
 * {@link io.taanielo.jmud.core.transport.Ferry} definitions from disk at startup. Only the
 * composition root may construct it (AGENTS.md §3.3); the domain depends on the
 * {@link io.taanielo.jmud.core.transport.FerryRepository} interface.
 */
@NullMarked
package io.taanielo.jmud.core.transport.repository.json;

import org.jspecify.annotations.NullMarked;
