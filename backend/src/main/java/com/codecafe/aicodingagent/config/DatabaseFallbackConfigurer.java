package com.codecafe.aicodingagent.config;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Lets the application run locally even when MySQL isn't installed/running:
 * before Spring Boot starts, this does a quick TCP reachability check
 * against the configured MySQL host/port and, if it's not reachable,
 * overrides the datasource properties (via System properties, which take
 * precedence over application.yml and environment variables) to point at a
 * local file-based H2 database instead - in MySQL compatibility mode, so
 * the rest of the application (entities, LONGTEXT columns, etc.) needs no
 * special-casing.
 *
 * Set DB_AUTO_FALLBACK_H2=false to disable this and fail fast on a
 * misconfigured/unreachable MySQL instead (e.g. for a real deployment).
 */
public final class DatabaseFallbackConfigurer {

    private static final String DEFAULT_MYSQL_URL =
            "jdbc:mysql://localhost:3306/ai_coding_agent?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true";
    private static final int DEFAULT_MYSQL_PORT = 3306;
    private static final int CONNECT_TIMEOUT_MILLIS = 1500;

    private DatabaseFallbackConfigurer() {
    }

    public static void configureIfNeeded() {
        configureIfNeeded(System::getenv, System::getProperty, DatabaseFallbackConfigurer::isReachable);
    }

    /** Package-visible overload for testing: injects env/system-property lookups and the reachability check. */
    static void configureIfNeeded(Function<String, String> env, Function<String, String> sysProp,
                                   BiPredicate<String, Integer> reachabilityCheck) {
        String fallbackEnabled = firstNonNull(sysProp.apply("DB_AUTO_FALLBACK_H2"), env.apply("DB_AUTO_FALLBACK_H2"), "true");
        if (!Boolean.parseBoolean(fallbackEnabled)) {
            return;
        }

        String configuredUrl = resolve("spring.datasource.url", "SPRING_DATASOURCE_URL", DEFAULT_MYSQL_URL, env, sysProp);
        HostPort hostPort;
        try {
            hostPort = parseHostPort(configuredUrl);
        } catch (IllegalArgumentException e) {
            // Not a recognizable jdbc:mysql:// URL (e.g. already pointed at H2/Postgres) - leave it alone.
            return;
        }

        if (reachabilityCheck.test(hostPort.host(), hostPort.port())) {
            return; // MySQL is reachable; use the configured datasource as-is.
        }

        String dataPath = firstNonNull(sysProp.apply("H2_DATA_PATH"), env.apply("H2_DATA_PATH"), "./data/ai_coding_agent");
        new File(dataPath).getAbsoluteFile().getParentFile().mkdirs();
        String h2Url = "jdbc:h2:file:" + dataPath + ";MODE=MySQL;DB_CLOSE_DELAY=-1";

        System.out.println("[ai-coding-agent] MySQL is not reachable at " + hostPort.host() + ":" + hostPort.port()
                + " - falling back to a local H2 database at " + dataPath
                + ".mv.db (set DB_AUTO_FALLBACK_H2=false to disable this).");

        System.setProperty("spring.datasource.url", h2Url);
        System.setProperty("spring.datasource.username", "sa");
        System.setProperty("spring.datasource.password", "");
        System.setProperty("spring.datasource.driver-class-name", "org.h2.Driver");
    }

    static HostPort parseHostPort(String jdbcUrl) {
        String prefix = "jdbc:mysql://";
        if (jdbcUrl == null || !jdbcUrl.startsWith(prefix)) {
            throw new IllegalArgumentException("Not a jdbc:mysql:// URL: " + jdbcUrl);
        }
        String rest = jdbcUrl.substring(prefix.length());
        int slash = rest.indexOf('/');
        String hostPortPart = slash >= 0 ? rest.substring(0, slash) : rest;
        int comma = hostPortPart.indexOf(',');
        if (comma >= 0) {
            hostPortPart = hostPortPart.substring(0, comma); // first host of a multi-host failover URL
        }
        int colon = hostPortPart.indexOf(':');
        String host = colon >= 0 ? hostPortPart.substring(0, colon) : hostPortPart;
        int port = colon >= 0 ? Integer.parseInt(hostPortPart.substring(colon + 1)) : DEFAULT_MYSQL_PORT;
        if (host.isBlank()) {
            throw new IllegalArgumentException("Could not determine host from: " + jdbcUrl);
        }
        return new HostPort(host, port);
    }

    static boolean isReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String resolve(String propertyKey, String envKey, String fallback,
                                   Function<String, String> env, Function<String, String> sysProp) {
        return firstNonNull(sysProp.apply(propertyKey), env.apply(envKey), fallback);
    }

    private static String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    record HostPort(String host, int port) {
    }
}
