package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.io.OutputStream;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class SocketCommand {
    /**
     * Interpret as Command
     */
    private static final int IAC = -1;

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
