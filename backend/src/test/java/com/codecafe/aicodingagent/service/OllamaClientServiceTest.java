package com.codecafe.aicodingagent.service;

import com.codecafe.aicodingagent.config.OllamaProperties;
import com.codecafe.aicodingagent.exception.OllamaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaClientServiceTest {

    private HttpServer fakeOllama;

    @AfterEach
    void stopFakeServer() {
        if (fakeOllama != null) {
            fakeOllama.stop(0);
        }
    }

    /**
     * Regression test for a real bug-shaped scenario found by manually running an actual
     * Ollama binary built from source in a sandbox with no model pulled: /api/chat returns
     * HTTP 404 with body {"error":"model 'X' not found"} - verified byte-for-byte against
     * the real server's response. This locks that exact behavior in with a fake server so
     * it doesn't require a live Ollama install to test.
     */
    @Test
    void chat_throwsModelNotAvailableOn404FromChatEndpoint() throws IOException {
        fakeOllama = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeOllama.createContext("/api/chat", exchange -> {
            byte[] body = "{\"error\":\"model 'llama3.1' not found\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeOllama.start();

        OllamaProperties props = new OllamaProperties(
                "http://localhost:" + fakeOllama.getAddress().getPort(), "llama3.1", 2, 5, 0);
        OllamaClientService client = new OllamaClientService(props, new ObjectMapper());

        assertThatThrownBy(() -> client.chat(List.of(ChatMessage.user("hi"))))
                .isInstanceOf(OllamaException.ModelNotAvailable.class)
                .hasMessageContaining("llama3.1");
    }

    @Test
    void checkHealth_reportsReachableButModelMissingWhenServerIsUpWithNoMatchingModel() throws IOException {
        fakeOllama = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeOllama.createContext("/api/tags", exchange -> {
            byte[] body = "{\"models\":[{\"name\":\"mistral:latest\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeOllama.start();

        OllamaProperties props = new OllamaProperties(
                "http://localhost:" + fakeOllama.getAddress().getPort(), "llama3.1", 2, 5, 0);
        OllamaClientService client = new OllamaClientService(props, new ObjectMapper());

        OllamaHealthStatus status = client.checkHealth();

        assertThat(status.reachable()).isTrue();
        assertThat(status.modelAvailable()).isFalse();
        assertThat(status.detail()).contains("not pulled yet");
    }

    @Test
    void checkHealth_reportsUnreachableWhenNothingIsListening() {
        OllamaProperties props = new OllamaProperties("http://localhost:1", "llama3.1", 1, 2, 0);
        OllamaClientService client = new OllamaClientService(props, new ObjectMapper());

        OllamaHealthStatus status = client.checkHealth();

        assertThat(status.reachable()).isFalse();
        assertThat(status.modelAvailable()).isFalse();
        assertThat(status.detail()).containsIgnoringCase("not reachable");
    }

    @Test
    void chat_throwsConnectionFailedWhenOllamaIsDown() {
        OllamaProperties props = new OllamaProperties("http://localhost:1", "llama3.1", 1, 2, 0);
        OllamaClientService client = new OllamaClientService(props, new ObjectMapper());

        assertThatThrownBy(() -> client.chat(List.of(ChatMessage.user("hi"))))
                .isInstanceOf(OllamaException.ConnectionFailed.class)
                .hasMessageContaining("Ollama");
    }
}
