package com.codecafe.aicodingagent.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseFallbackConfigurerTest {

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty("spring.datasource.url");
        System.clearProperty("spring.datasource.username");
        System.clearProperty("spring.datasource.password");
        System.clearProperty("spring.datasource.driver-class-name");
    }

    @Test
    void parseHostPort_readsHostAndPortFromAFullMySqlUrl() {
        var hp = DatabaseFallbackConfigurer.parseHostPort(
                "jdbc:mysql://localhost:3306/ai_coding_agent?useSSL=false&serverTimezone=UTC");
        assertThat(hp.host()).isEqualTo("localhost");
        assertThat(hp.port()).isEqualTo(3306);
    }

    @Test
    void parseHostPort_defaultsToPort3306WhenNotSpecified() {
        var hp = DatabaseFallbackConfigurer.parseHostPort("jdbc:mysql://dbhost/mydb");
        assertThat(hp.host()).isEqualTo("dbhost");
        assertThat(hp.port()).isEqualTo(3306);
    }

    @Test
    void parseHostPort_handlesUrlWithNoPathOrParams() {
        var hp = DatabaseFallbackConfigurer.parseHostPort("jdbc:mysql://myhost:3307");
        assertThat(hp.host()).isEqualTo("myhost");
        assertThat(hp.port()).isEqualTo(3307);
    }

    @Test
    void parseHostPort_usesFirstHostOfAMultiHostFailoverUrl() {
        var hp = DatabaseFallbackConfigurer.parseHostPort("jdbc:mysql://host1:3306,host2:3306/db");
        assertThat(hp.host()).isEqualTo("host1");
        assertThat(hp.port()).isEqualTo(3306);
    }

    @Test
    void parseHostPort_rejectsNonMySqlUrl() {
        assertThatThrownBy(() -> DatabaseFallbackConfigurer.parseHostPort("jdbc:h2:mem:testdb"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isReachable_trueForAnOpenLocalPort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            assertThat(DatabaseFallbackConfigurer.isReachable("localhost", serverSocket.getLocalPort())).isTrue();
        }
    }

    @Test
    void isReachable_falseForAClosedPort() {
        assertThat(DatabaseFallbackConfigurer.isReachable("localhost", 1)).isFalse();
    }

    @Test
    void configureIfNeeded_fallsBackToH2WhenMySqlIsUnreachable() {
        Map<String, String> env = new HashMap<>();
        DatabaseFallbackConfigurer.configureIfNeeded(env::get, key -> null, (host, port) -> false);

        assertThat(System.getProperty("spring.datasource.url")).startsWith("jdbc:h2:file:");
        assertThat(System.getProperty("spring.datasource.driver-class-name")).isEqualTo("org.h2.Driver");
    }

    @Test
    void configureIfNeeded_leavesConfigurationUntouchedWhenMySqlIsReachable() {
        Map<String, String> env = new HashMap<>();
        DatabaseFallbackConfigurer.configureIfNeeded(env::get, key -> null, (host, port) -> true);

        assertThat(System.getProperty("spring.datasource.url")).isNull();
    }

    @Test
    void configureIfNeeded_doesNothingWhenFallbackIsDisabled() {
        Map<String, String> env = new HashMap<>();
        env.put("DB_AUTO_FALLBACK_H2", "false");

        DatabaseFallbackConfigurer.configureIfNeeded(env::get, key -> null, (host, port) -> false);

        assertThat(System.getProperty("spring.datasource.url")).isNull();
    }

    @Test
    void configureIfNeeded_respectsAConfiguredCustomMySqlUrl() {
        Map<String, String> env = new HashMap<>();
        env.put("SPRING_DATASOURCE_URL", "jdbc:mysql://custom-host:3333/db");

        final String[] checkedHost = new String[1];
        final int[] checkedPort = new int[1];
        DatabaseFallbackConfigurer.configureIfNeeded(env::get, key -> null, (host, port) -> {
            checkedHost[0] = host;
            checkedPort[0] = port;
            return true;
        });

        assertThat(checkedHost[0]).isEqualTo("custom-host");
        assertThat(checkedPort[0]).isEqualTo(3333);
    }
}
