package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Verifies {@link TelnetLineReader} assembles correct lines regardless of how the
 * underlying transport fragments or coalesces bytes across reads, and that it
 * strips telnet IAC sequences and enforces the maximum line length.
 */
class TelnetLineReaderTest {

    @Test
    void oneLineInOneRead() throws IOException {
        TelnetLineReader reader = readerFor(chunk("look\n"));

        TelnetLineReader.Result result = reader.readLine();

        assertLine("look", result);
    }

    @Test
    void lineDeliveredByteByByte() throws IOException {
        TelnetLineReader reader = readerFor(byteByByte("say hi\n"));

        TelnetLineReader.Result result = reader.readLine();

        assertLine("say hi", result);
    }

    @Test
    void twoLinesCoalescedInOneRead() throws IOException {
        TelnetLineReader reader = readerFor(chunk("north\nsouth\n"));

        assertLine("north", reader.readLine());
        assertLine("south", reader.readLine());
    }

    @Test
    void carriageReturnAsLastByteOfOneReadWithLineFeedInNext() throws IOException {
        TelnetLineReader reader = readerFor(chunk("look\r"), chunk("\n"));

        TelnetLineReader.Result result = reader.readLine();

        assertLine("look", result);
    }

    @Test
    void iacNegotiationInterleavedMidLine() throws IOException {
        // IAC WILL ECHO (255, 251, 1) spliced into the middle of a command line.
        byte[] withNegotiation = concat(
            "lo".getBytes(StandardCharsets.UTF_8),
            new byte[] {(byte) 255, (byte) 251, 1},
            "ok\n".getBytes(StandardCharsets.UTF_8)
        );
        TelnetLineReader reader = readerFor(withNegotiation);

        TelnetLineReader.Result result = reader.readLine();

        assertLine("look", result);
    }

    @Test
    void iacSubnegotiationIsSkipped() throws IOException {
        // IAC SB NAWS ... IAC SE spliced mid-line.
        byte[] withSubnegotiation = concat(
            "lo".getBytes(StandardCharsets.UTF_8),
            new byte[] {(byte) 255, (byte) 250, 31, 0, 80, 0, 24, (byte) 255, (byte) 240},
            "ok\n".getBytes(StandardCharsets.UTF_8)
        );
        TelnetLineReader reader = readerFor(withSubnegotiation);

        TelnetLineReader.Result result = reader.readLine();

        assertLine("look", result);
    }

    @Test
    void interruptProcessEndsLikeEndOfStream() throws IOException {
        TelnetLineReader reader = readerFor(new byte[] {(byte) 255, (byte) 244});

        TelnetLineReader.Result result = reader.readLine();

        assertInstanceOf(TelnetLineReader.Result.EndOfStream.class, result);
    }

    @Test
    void endOfStreamReturnsEndOfStreamResult() throws IOException {
        TelnetLineReader reader = readerFor();

        TelnetLineReader.Result result = reader.readLine();

        assertInstanceOf(TelnetLineReader.Result.EndOfStream.class, result);
    }

    @Test
    void overLengthLineIsDiscardedAndReported() throws IOException {
        String longLine = "a".repeat(600) + "\n";
        TelnetLineReader reader = new TelnetLineReader(new ByteArrayInputStream(
            longLine.getBytes(StandardCharsets.UTF_8)), 16);

        TelnetLineReader.Result result = reader.readLine();

        assertInstanceOf(TelnetLineReader.Result.Oversized.class, result);
    }

    @Test
    void readerRecoversAfterOverLengthLine() throws IOException {
        String input = "a".repeat(600) + "\nlook\n";
        TelnetLineReader reader = new TelnetLineReader(new ByteArrayInputStream(
            input.getBytes(StandardCharsets.UTF_8)), 16);

        assertInstanceOf(TelnetLineReader.Result.Oversized.class, reader.readLine());
        assertLine("look", reader.readLine());
    }

    @Test
    void utf8MultiByteCharacterSplitAcrossTwoReads() throws IOException {
        // "café\n" - the 'é' (U+00E9) is 2 bytes in UTF-8: 0xC3 0xA9.
        byte[] full = "café\n".getBytes(StandardCharsets.UTF_8);
        // Split so the multi-byte character straddles two reads.
        int splitPoint = full.length - 2;
        byte[] first = new byte[splitPoint + 1];
        System.arraycopy(full, 0, first, 0, splitPoint + 1);
        byte[] second = new byte[full.length - first.length];
        System.arraycopy(full, first.length, second, 0, second.length);

        TelnetLineReader reader = readerFor(first, second);

        TelnetLineReader.Result result = reader.readLine();

        assertLine("café", result);
    }

    private static void assertLine(String expected, TelnetLineReader.Result result) {
        assertInstanceOf(TelnetLineReader.Result.Line.class, result);
        assertEquals(expected, ((TelnetLineReader.Result.Line) result).text());
    }

    private static byte[] chunk(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static List<byte[]> byteByByte(String text) {
        List<byte[]> chunks = new ArrayList<>();
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            chunks.add(new byte[] {b});
        }
        return chunks;
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] result = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    private static TelnetLineReader readerFor(byte[]... chunks) {
        return new TelnetLineReader(new ChunkedInputStream(List.of(chunks)));
    }

    private static TelnetLineReader readerFor(List<byte[]> chunks) {
        return new TelnetLineReader(new ChunkedInputStream(chunks));
    }

    /**
     * An {@link InputStream} that returns exactly one supplied chunk per {@code read} call,
     * simulating arbitrary fragmentation or coalescing of network reads.
     */
    private static final class ChunkedInputStream extends InputStream {
        private final Deque<byte[]> chunks;

        ChunkedInputStream(List<byte[]> chunks) {
            this.chunks = new ArrayDeque<>(chunks);
        }

        @Override
        public int read() {
            throw new UnsupportedOperationException("Not used by TelnetLineReader");
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (chunks.isEmpty()) {
                return -1;
            }
            byte[] next = chunks.poll();
            int count = Math.min(len, next.length);
            System.arraycopy(next, 0, b, off, count);
            return count;
        }
    }
}
