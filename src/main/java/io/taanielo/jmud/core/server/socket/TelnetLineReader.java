package io.taanielo.jmud.core.server.socket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Assembles telnet input bytes read from an {@link InputStream} into complete,
 * LF-terminated lines, independent of how the underlying network reads fragment
 * or coalesce data.
 *
 * <p>A single {@link #readLine()} call may issue any number of underlying
 * {@code InputStream#read} calls before a full line is available; a client that
 * sends one byte per read (character-mode telnet) is handled identically to one
 * that sends whole lines or several lines in a single packet.
 *
 * <p>Telnet IAC negotiation sequences (option negotiation and subnegotiation) are
 * recognized and stripped from the line content in-stream. An {@code IAC IP}
 * (Interrupt Process) sequence is treated the same as end of stream, matching the
 * previous behavior of disconnecting on Ctrl+C.
 *
 * <p>Lines longer than the configured maximum are discarded (bytes are dropped
 * until the next line feed) and reported as {@link Result.Oversized} so the caller
 * can warn the client instead of crashing or disconnecting.
 */
public class TelnetLineReader {

    /** Interpret As Command. */
    private static final int IAC = 255;
    /** Subnegotiation begin. */
    private static final int SB = 250;
    /** Subnegotiation end. */
    private static final int SE = 240;
    /** Will perform option. */
    private static final int WILL = 251;
    /** Won't perform option. */
    private static final int WONT = 252;
    /** Do perform option. */
    private static final int DO = 253;
    /** Don't perform option. */
    private static final int DONT = 254;
    /** Interrupt Process. */
    private static final int IP = 244;

    private static final int DEFAULT_MAX_LINE_LENGTH = 512;
    private static final int SCRATCH_BUFFER_SIZE = 1024;

    private enum State {
        NORMAL,
        IAC_SEEN,
        NEGOTIATION_OPTION,
        SUBNEGOTIATION,
        SUBNEGOTIATION_IAC_SEEN
    }

    /**
     * Outcome of assembling telnet input: a complete line, end of stream (including
     * an Interrupt Process command), or an over-length line that was discarded.
     */
    public sealed interface Result {
        /** A complete, decoded line of input. */
        record Line(String text) implements Result {}

        /** The stream ended, or the client sent Interrupt Process (Ctrl+C). */
        record EndOfStream() implements Result {}

        /** A line exceeded the configured maximum length and was discarded. */
        record Oversized() implements Result {}
    }

    private final InputStream input;
    private final int maxLineLength;
    private final byte[] scratch = new byte[SCRATCH_BUFFER_SIZE];
    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();

    private State state = State.NORMAL;
    private boolean oversized;
    private int scratchPosition;
    private int scratchLength;

    /**
     * Creates a reader with the default maximum line length (512 bytes).
     */
    public TelnetLineReader(InputStream input) {
        this(input, DEFAULT_MAX_LINE_LENGTH);
    }

    /**
     * Creates a reader with an explicit maximum line length.
     */
    public TelnetLineReader(InputStream input, int maxLineLength) {
        this.input = Objects.requireNonNull(input, "Input stream is required");
        if (maxLineLength <= 0) {
            throw new IllegalArgumentException("Max line length must be positive");
        }
        this.maxLineLength = maxLineLength;
    }

    /**
     * Blocks until a full line has been assembled, the stream ends, or an
     * Interrupt Process command is received.
     *
     * @throws IOException if the underlying stream read fails
     */
    public Result readLine() throws IOException {
        while (true) {
            if (scratchPosition >= scratchLength) {
                int n = input.read(scratch);
                if (n == -1) {
                    return new Result.EndOfStream();
                }
                scratchPosition = 0;
                scratchLength = n;
            }
            while (scratchPosition < scratchLength) {
                Result result = consume(scratch[scratchPosition++]);
                if (result != null) {
                    return result;
                }
            }
        }
    }

    private Result consume(byte rawByte) {
        int value = rawByte & 0xFF;
        return switch (state) {
            case NORMAL -> consumeNormal(value);
            case IAC_SEEN -> consumeIacSeen(value);
            case NEGOTIATION_OPTION -> {
                // Option byte of WILL/WONT/DO/DONT consumed; we do not reply.
                state = State.NORMAL;
                yield null;
            }
            case SUBNEGOTIATION -> {
                if (value == IAC) {
                    state = State.SUBNEGOTIATION_IAC_SEEN;
                }
                yield null;
            }
            case SUBNEGOTIATION_IAC_SEEN -> {
                state = value == SE ? State.NORMAL : State.SUBNEGOTIATION;
                yield null;
            }
        };
    }

    private Result consumeNormal(int value) {
        if (value == IAC) {
            state = State.IAC_SEEN;
            return null;
        }
        if (value == '\n') {
            return emitLine();
        }
        appendToLine(value);
        return null;
    }

    private Result consumeIacSeen(int value) {
        state = State.NORMAL;
        if (value == IP) {
            return new Result.EndOfStream();
        }
        if (value == WILL || value == WONT || value == DO || value == DONT) {
            state = State.NEGOTIATION_OPTION;
            return null;
        }
        if (value == SB) {
            state = State.SUBNEGOTIATION;
            return null;
        }
        if (value == IAC) {
            // Escaped 0xFF data byte.
            appendToLine(value);
            return null;
        }
        // Other two-byte IAC commands (NOP, AYT, DM, ...) carry no line data.
        return null;
    }

    private void appendToLine(int value) {
        if (oversized) {
            return;
        }
        if (lineBuffer.size() >= maxLineLength) {
            oversized = true;
            return;
        }
        lineBuffer.write(value);
    }

    private Result emitLine() {
        byte[] bytes = lineBuffer.toByteArray();
        lineBuffer.reset();
        boolean wasOversized = oversized;
        oversized = false;
        if (wasOversized) {
            return new Result.Oversized();
        }
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') {
            length--;
        }
        return new Result.Line(new String(bytes, 0, length, StandardCharsets.UTF_8));
    }
}
