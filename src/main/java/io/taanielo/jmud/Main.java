package io.taanielo.jmud;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.Server;
import io.taanielo.jmud.core.server.socket.DefaultClientPool;
import io.taanielo.jmud.core.server.socket.GameContext;
import io.taanielo.jmud.core.server.socket.SocketServer;
import io.taanielo.jmud.core.server.ssh.SshServer;

@Slf4j
public class Main {

    public static void main(String[] args) {
        int telnetPort = resolvePort(args, "--telnet-port", "JMUD_TELNET_PORT", 4444);
        int sshPort = resolvePort(args, "--ssh-port", "JMUD_SSH_PORT", 2222);
        ClientPool clientPool = new DefaultClientPool();
        GameContext context = GameContext.create();
        Server telnetServer = new SocketServer(telnetPort, context, clientPool);
        Server sshServer = new SshServer(sshPort, context, clientPool);
        log.info("Starting servers ..");
        Thread telnetThread = Thread.ofVirtual().name("telnet-server").start(telnetServer);
        Thread sshThread = Thread.ofVirtual().name("ssh-server").start(sshServer);
        log.info("Servers started");
        try {
            telnetThread.join();
            sshThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Main thread interrupted, stopping servers");
            telnetThread.interrupt();
            sshThread.interrupt();
        }
    }

    private static int resolvePort(String[] args, String argName, String envName, int defaultPort) {
        String argValue = findArgValue(args, argName);
        if (argValue != null) {
            Integer parsed = parsePort(argValue, argName);
            if (parsed != null) {
                return parsed;
            }
        }
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            Integer parsed = parsePort(envValue, envName);
            if (parsed != null) {
                return parsed;
            }
        }
        return defaultPort;
    }

    private static String findArgValue(String[] args, String argName) {
        for (int i = 0; i < args.length - 1; i++) {
            if (argName.equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static Integer parsePort(String value, String source) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0 || parsed > 65535) {
                throw new NumberFormatException("Port out of range");
            }
            return parsed;
        } catch (NumberFormatException e) {
            log.warn("Invalid port from {}: {}", source, value);
            return null;
        }
    }
}
