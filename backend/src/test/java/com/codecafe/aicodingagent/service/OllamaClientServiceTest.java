package com.codecafe.aicodingagent.service;

import com.codecafe.aicodingagent.config.OllamaProperties;
import com.codecafe.aicodingagent.exception.OllamaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaClientServiceTest {

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
