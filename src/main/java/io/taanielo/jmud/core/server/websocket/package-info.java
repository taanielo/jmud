/**
 * WebSocket server endpoint — a third transport alongside telnet and SSH (issue #526).
 *
 * <p>A browser client connects over {@code ws://} (or {@code wss://} when TLS is terminated at a
 * reverse proxy). Each connection runs the same in-band login flow, the same {@link
 * io.taanielo.jmud.core.server.socket.SocketClient} reader loop, and the same single-writer tick
 * model as telnet — only the wire framing differs. The transport speaks a minimal, self-contained
 * subset of RFC 6455 (text frames, ping/pong, close) built directly on a blocking {@link
 * java.net.Socket} and one virtual thread per connection (AGENTS.md §4.3, §5); no telnet control
 * bytes are ever emitted on this wire, and ANSI escape sequences pass through unmodified.
 */
@NullMarked
package io.taanielo.jmud.core.server.websocket;

import org.jspecify.annotations.NullMarked;
