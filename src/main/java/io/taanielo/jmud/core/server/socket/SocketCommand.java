package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class SocketCommand {
    /**
     * Interpret as Command
     */
    private static final int IAC = -1;
    /**
     * Interrupt process (user pressed Ctrl + C)
     */
    private static final int IP = -12;

    boolean isIAC(byte[] bytes) {
        return bytes.length > 0 && IAC == bytes[0];
    }

    boolean isIP(byte[] bytes) {
        return bytes.length > 1 && isIAC(bytes) && bytes[1] == IP;
    }

    String readString(byte[] bytes) {
        if (bytes.length == 0) {
            return "";
        }
        int crlfPos = 0;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == 13 && bytes[i + 1] == 10 || bytes[i] == 10) {
                crlfPos = i;
                break;
            }
        }
        return new String(bytes, 0, crlfPos, StandardCharsets.UTF_8);
    }

    void disableEcho(OutputStream output) throws IOException {
        log.debug("Disabling local echo");
        output.write(IAC);
        output.write(0xFB);
        output.write(0x01);
        output.flush();
    }

    void enableEcho(OutputStream output) throws IOException {
        log.debug("Enabling local echo");
        output.write(IAC);
        output.write(0xFC);
        output.write(0x01);
        output.flush();
    }
}
