package io.taanielo.jmud;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.bootstrap.DataValidator;
import io.taanielo.jmud.bootstrap.GameContext;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.Server;
import io.taanielo.jmud.core.server.socket.DefaultClientPool;
import io.taanielo.jmud.core.server.socket.ShutdownCoordinator;
import io.taanielo.jmud.core.server.socket.SocketServer;
import io.taanielo.jmud.core.server.ssh.SshServer;
import io.taanielo.jmud.core.server.websocket.WebSocketServer;
import io.taanielo.jmud.core.server.websocket.WsOriginPolicy;

@Slf4j
public class Main {

    public static void main(String[] args) {
        if (hasFlag(args, "--validate-data")) {
            System.exit(runDataValidation());
        }

        boolean telnetEnabled = resolveBoolean(args, "--telnet-enabled", "JMUD_TELNET_ENABLED", true);
        String telnetHost = resolveHost(args, "--telnet-host", "JMUD_TELNET_HOST", "127.0.0.1");
        int telnetPort = resolvePort(args, "--telnet-port", "JMUD_TELNET_PORT", 4444);
        String sshHost = resolveHost(args, "--ssh-host", "JMUD_SSH_HOST", "0.0.0.0");
        int sshPort = resolvePort(args, "--ssh-port", "JMUD_SSH_PORT", 2222);
        boolean wsEnabled = resolveBoolean(args, "--ws-enabled", "JMUD_WS_ENABLED", true);
        String wsHost = resolveHost(args, "--ws-host", "JMUD_WS_HOST", "127.0.0.1");
        int wsPort = resolvePort(args, "--ws-port", "JMUD_WS_PORT", 8080);
        WsOriginPolicy wsOriginPolicy = resolveWsOriginPolicy(args);
        ClientPool clientPool = new DefaultClientPool();
        GameContext context = GameContext.create(clientPool);
        context.tickScheduler().start();

        Server telnetServer = telnetEnabled ? new SocketServer(telnetHost, telnetPort, context, clientPool) : null;
        Server sshServer = new SshServer(sshHost, sshPort, context, clientPool);
        Server wsServer = wsEnabled ? new WebSocketServer(wsHost, wsPort, context, clientPool, wsOriginPolicy) : null;
        List<Server> servers = new ArrayList<>();
        if (telnetServer != null) {
            servers.add(telnetServer);
        }
        servers.add(sshServer);
        if (wsServer != null) {
            servers.add(wsServer);
        }

        ShutdownCoordinator shutdownCoordinator = new ShutdownCoordinator(
            servers,
            clientPool,
            context.tickScheduler(),
            context.tickRegistry(),
            context.persistenceQueue(),
            context.auditService()
        );
        // Install the shutdown sequence into the late-bound handle so the wizard SHUTDOWN command
        // (registered inside GameContext, before the coordinator exists) can trigger it.
        context.shutdownHandle().install(shutdownCoordinator::shutdown);
        Runtime.getRuntime().addShutdownHook(
            Thread.ofVirtual().name("jmud-shutdown").unstarted(shutdownCoordinator::shutdown)
        );

        log.info("Starting servers ..");
        if (telnetEnabled && !isLoopbackHost(telnetHost)) {
            log.warn("Telnet is enabled on non-loopback host {}. Telnet is unencrypted.", telnetHost);
        }
        if (wsEnabled && !isLoopbackHost(wsHost)) {
            log.warn("WebSocket is enabled on non-loopback host {}. Plain ws:// is unencrypted; "
                + "terminate TLS (wss://) at a reverse proxy for public deployments.", wsHost);
        }
        Thread telnetThread = telnetEnabled ? Thread.ofVirtual().name("telnet-server").start(telnetServer) : null;
        Thread sshThread = Thread.ofVirtual().name("ssh-server").start(sshServer);
        Thread wsThread = wsEnabled ? Thread.ofVirtual().name("ws-server").start(wsServer) : null;
        log.info("Servers started");
        try {
            if (telnetThread != null) {
                telnetThread.join();
            }
            sshThread.join();
            if (wsThread != null) {
                wsThread.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Main thread interrupted, stopping servers");
            if (telnetThread != null) {
                telnetThread.interrupt();
            }
            sshThread.interrupt();
            if (wsThread != null) {
                wsThread.interrupt();
            }
        }
    }

    /**
     * Resolves the WebSocket {@code Origin} allowlist from {@code --ws-allowed-origins} or
     * {@code JMUD_WS_ALLOWED_ORIGINS} (comma-separated). An unset/blank value yields a permissive
     * policy, matching the loopback-bound default deployment.
     */
    private static WsOriginPolicy resolveWsOriginPolicy(String[] args) {
        String value = findArgValue(args, "--ws-allowed-origins");
        if (value == null || value.isBlank()) {
            value = System.getenv("JMUD_WS_ALLOWED_ORIGINS");
        }
        if (value == null || value.isBlank()) {
            return WsOriginPolicy.permissive();
        }
        List<String> origins = List.of(value.split(",", -1));
        return WsOriginPolicy.of(origins);
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

    private static boolean hasFlag(String[] args, String flagName) {
        for (String arg : args) {
            if (flagName.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs the data-validation mode: constructs the data validator, scans every
     * domain directory under {@code data/} and the player-save directory, and
     * prints a per-domain summary.
     *
     * @return {@code 0} when all files parse successfully, {@code 1} if any file
     *         failed to parse
     */
    private static int runDataValidation() {
        log.info("Running data validation …");
        DataValidator validator = new DataValidator();
        DataValidator.ValidationReport report = validator.validate(Path.of("data"), Path.of("players"));

        for (DataValidator.DomainResult domain : report.domains()) {
            if (domain.clean()) {
                System.out.printf("  [OK]   %-12s  %d file(s)%n", domain.domain(), domain.fileCount());
            } else {
                System.out.printf("  [FAIL] %-12s  %d error(s) / %d file(s)%n",
                    domain.domain(), domain.errors().size(), domain.fileCount());
                for (DataValidator.FileError error : domain.errors()) {
                    System.out.printf("         %s%n", error.path());
                    System.out.printf("         -> %s%n", error.error());
                }
            }
        }

        System.out.printf("%n");
        if (report.clean()) {
            System.out.printf("Data validation PASSED: %d file(s) across %d domain(s)%n",
                report.totalFiles(), report.domains().size());
            return 0;
        } else {
            System.out.printf("Data validation FAILED: %d error(s) in %d file(s) scanned%n",
                report.totalErrors(), report.totalFiles());
            return 1;
        }
    }
}
