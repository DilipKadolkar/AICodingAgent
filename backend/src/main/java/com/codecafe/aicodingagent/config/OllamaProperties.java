package com.codecafe.aicodingagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(
        String baseUrl,
        String model,
        int connectTimeoutSeconds,
        int requestTimeoutSeconds,
        int maxRetries) {
}
