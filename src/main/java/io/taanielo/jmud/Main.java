package io.taanielo.jmud;

import java.util.Locale;

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
        boolean telnetEnabled = resolveBoolean(args, "--telnet-enabled", "JMUD_TELNET_ENABLED", true);
        String telnetHost = resolveHost(args, "--telnet-host", "JMUD_TELNET_HOST", "127.0.0.1");
        int telnetPort = resolvePort(args, "--telnet-port", "JMUD_TELNET_PORT", 4444);
        String sshHost = resolveHost(args, "--ssh-host", "JMUD_SSH_HOST", "0.0.0.0");
        int sshPort = resolvePort(args, "--ssh-port", "JMUD_SSH_PORT", 2222);
        ClientPool clientPool = new DefaultClientPool();
        GameContext context = GameContext.create();
        context.tickScheduler().start();
        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().name("jmud-shutdown").unstarted(() -> {
            context.tickScheduler().stop();
            context.tickRegistry().clear();
        }));

        Server telnetServer = telnetEnabled ? new SocketServer(telnetHost, telnetPort, context, clientPool) : null;
        Server sshServer = new SshServer(sshHost, sshPort, context, clientPool);
        log.info("Starting servers ..");
        if (telnetEnabled && !isLoopbackHost(telnetHost)) {
            log.warn("Telnet is enabled on non-loopback host {}. Telnet is unencrypted.", telnetHost);
        }
        Thread telnetThread = telnetEnabled ? Thread.ofVirtual().name("telnet-server").start(telnetServer) : null;
        Thread sshThread = Thread.ofVirtual().name("ssh-server").start(sshServer);
        log.info("Servers started");
        try {
            if (telnetThread != null) {
                telnetThread.join();
            }
            sshThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Main thread interrupted, stopping servers");
            if (telnetThread != null) {
                telnetThread.interrupt();
            }
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

    private static boolean resolveBoolean(String[] args, String argName, String envName, boolean defaultValue) {
        String argValue = findArgValue(args, argName);
        if (argValue != null) {
            Boolean parsed = parseBoolean(argValue, argName);
            if (parsed != null) {
                return parsed;
            }
        }
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            Boolean parsed = parseBoolean(envValue, envName);
            if (parsed != null) {
                return parsed;
            }
        }
        return defaultValue;
    }

    private static String resolveHost(String[] args, String argName, String envName, String defaultValue) {
        String argValue = findArgValue(args, argName);
        if (argValue != null && !argValue.isBlank()) {
            return argValue.trim();
        }
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }
        return defaultValue;
    }

    private static Boolean parseBoolean(String value, String source) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "1", "yes", "y" -> true;
            case "false", "0", "no", "n" -> false;
            default -> {
                log.warn("Invalid boolean from {}: {}", source, value);
                yield null;
            }
        };
    }

    private static boolean isLoopbackHost(String host) {
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return "127.0.0.1".equals(normalized) || "localhost".equals(normalized) || "::1".equals(normalized);
    }
}
