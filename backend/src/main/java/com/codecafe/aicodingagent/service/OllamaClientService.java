package com.codecafe.aicodingagent.service;

import com.codecafe.aicodingagent.config.OllamaProperties;
import com.codecafe.aicodingagent.exception.OllamaException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Talks to the locally running Ollama instance. This is the only source of
 * AI reasoning in the application - no external/cloud coding-agent APIs are
 * called anywhere.
 */
@Service
public class OllamaClientService {

    private static final Logger log = LoggerFactory.getLogger(OllamaClientService.class);

    public static final String SYSTEM_PROMPT = """
            You are a professional, conversational AI software engineering assistant \
            running entirely on the user's local machine via Ollama. You help with \
            coding tasks inside a single target repository.

            Ground rules:
            - Always explore the repository (list files, search code, read files, or \
            analyze its structure) before recommending or making any change. Never \
            propose a modification to a file you have not actually looked at in this \
            conversation.
            - When you want to use a tool, respond with ONLY a single fenced JSON code \
            block of the exact form:
              ```json
              {"tool": "TOOL_NAME", "params": { ... }}
              ```
              Do not add any other text in that message when calling a tool.
            - Available tools: LIST_FILES, SEARCH_CODE, READ_FILE, ANALYZE_STRUCTURE, \
            IDENTIFY_RELEVANT_FILES, CREATE_FILE, MODIFY_FILE, REPLACE_CODE_SECTION, \
            EXECUTE_COMMAND, VERIFY_CHANGES.
            - When you are done and ready to answer the user directly, respond with \
            plain natural-language text (no JSON tool block). Explain what you found, \
            what you changed, and the reasoning behind your recommendation.
            - Be concise, professional, and conversational. Never fabricate file \
            contents, command output, or test results you have not actually seen via a \
            tool call.
            """;

    private final OllamaProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaClientService(OllamaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.connectTimeoutSeconds())))
                .build();
    }

    /**
     * Sends the full conversation to Ollama's /api/chat endpoint and returns the
     * assistant's complete reply. Internally consumes Ollama's NDJSON streaming
     * response and reassembles it; {@code onToken} (optional) is invoked with
     * each incremental content chunk as it arrives.
     */
    public String chat(List<ChatMessage> messages, Consumer<String> onToken) {
        String requestBody = buildRequestBody(messages);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.baseUrl() + "/api/chat"))
                .timeout(Duration.ofSeconds(Math.max(1, properties.requestTimeoutSeconds())))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        int attempt = 0;
        while (true) {
            attempt++;
            try {
                HttpResponse<Stream<String>> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
                return handleResponse(response, onToken);
            } catch (ConnectException e) {
                if (attempt > properties.maxRetries()) {
                    throw new OllamaException.ConnectionFailed(properties.baseUrl(), e);
                }
                log.warn("Ollama connection attempt {} failed, retrying...", attempt);
                sleepBriefly();
            } catch (HttpTimeoutException e) {
                throw new OllamaException.RequestTimedOut(properties.baseUrl(), e);
            } catch (IOException e) {
                if (attempt > properties.maxRetries()) {
                    throw new OllamaException.ConnectionFailed(properties.baseUrl(), e);
                }
                log.warn("Ollama I/O error on attempt {}, retrying...", attempt);
                sleepBriefly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OllamaException.ConnectionFailed(properties.baseUrl(), e);
            }
        }
    }

    public String chat(List<ChatMessage> messages) {
        return chat(messages, token -> { });
    }

    private String handleResponse(HttpResponse<Stream<String>> response, Consumer<String> onToken) {
        int status = response.statusCode();
        if (status == 404) {
            throw new OllamaException.ModelNotAvailable(properties.model());
        }
        if (status >= 400) {
            String body = response.body().collect(Collectors.joining("\n"));
            throw new OllamaException.MalformedResponse("HTTP " + status + ": " + body, null);
        }

        StringBuilder full = new StringBuilder();
        for (String line : (Iterable<String>) response.body()::iterator) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode node;
            try {
                node = objectMapper.readTree(line);
            } catch (IOException e) {
                throw new OllamaException.MalformedResponse(
                        "Could not parse a line of the Ollama stream: " + line, e);
            }
            if (node.has("error")) {
                String errText = node.get("error").asText();
                if (errText.toLowerCase().contains("not found")) {
                    throw new OllamaException.ModelNotAvailable(properties.model());
                }
                throw new OllamaException.MalformedResponse(errText, null);
            }
            JsonNode messageNode = node.get("message");
            if (messageNode != null && messageNode.has("content")) {
                String chunk = messageNode.get("content").asText();
                if (!chunk.isEmpty()) {
                    full.append(chunk);
                    onToken.accept(chunk);
                }
            }
        }
        if (full.isEmpty()) {
            throw new OllamaException.MalformedResponse("Ollama returned an empty response.", null);
        }
        return full.toString();
    }

    public OllamaHealthStatus checkHealth() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.baseUrl() + "/api/tags"))
                .timeout(Duration.ofSeconds(Math.max(1, properties.connectTimeoutSeconds())))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return OllamaHealthStatus.unreachable(properties.baseUrl());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode models = root.get("models");
            boolean modelPresent = false;
            if (models != null && models.isArray()) {
                for (JsonNode m : models) {
                    String name = m.path("name").asText("");
                    String model = m.path("model").asText("");
                    if (matchesConfiguredModel(name) || matchesConfiguredModel(model)) {
                        modelPresent = true;
                        break;
                    }
                }
            }
            return modelPresent ? OllamaHealthStatus.ok(properties.model())
                    : OllamaHealthStatus.modelMissing(properties.model());
        } catch (IOException e) {
            return OllamaHealthStatus.unreachable(properties.baseUrl());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return OllamaHealthStatus.unreachable(properties.baseUrl());
        }
    }

    private boolean matchesConfiguredModel(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        String configured = properties.model();
        return candidate.equals(configured) || candidate.startsWith(configured + ":");
    }

    private String buildRequestBody(List<ChatMessage> messages) {
        try {
            Map<String, Object> body = Map.of(
                    "model", properties.model(),
                    "messages", messages.stream()
                            .map(m -> Map.of("role", m.role(), "content", m.content()))
                            .toList(),
                    "stream", true
            );
            return objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize Ollama request body", e);
        }
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
