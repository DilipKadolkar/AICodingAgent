package com.codecafe.aicodingagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @TempDir
    Path repoRoot;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(repoRoot.resolve("README.md"), "hello");
    }

    private String createSession() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("repositoryPath", repoRoot.toString(), "title", "T"));
        String response = mockMvc.perform(post("/api/sessions")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    @Test
    void createSession_happyPath() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("repositoryPath", repoRoot.toString(), "title", "My session"));
        mockMvc.perform(post("/api/sessions").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("My session"))
                .andExpect(jsonPath("$.status").value("IDLE"));
    }

    @Test
    void createSession_rejectsNonExistentRepositoryPath() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("repositoryPath", "/no/such/path/at/all"));
        mockMvc.perform(post("/api/sessions").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void createSession_rejectsBlankRepositoryPath() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("repositoryPath", ""));
        mockMvc.perform(post("/api/sessions").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listSessions_returnsCreatedSession() throws Exception {
        createSession();
        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());
    }

    @Test
    void getSession_returnsNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/sessions/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("session_not_found"));
    }

    @Test
    void getSession_returnsFullDetailWithEmptyTranscriptForNewSession() throws Exception {
        String id = createSession();
        mockMvc.perform(get("/api/sessions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").isEmpty())
                .andExpect(jsonPath("$.toolCalls").isEmpty());
    }

    @Test
    void postMessage_rejectsEmptyContent() throws Exception {
        String id = createSession();
        String body = objectMapper.writeValueAsString(Map.of("content", "   "));
        mockMvc.perform(post("/api/sessions/" + id + "/messages").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void postMessage_returnsNotFoundForUnknownSession() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("content", "hello"));
        mockMvc.perform(post("/api/sessions/does-not-exist/messages").contentType("application/json").content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void postMessage_returnsServiceUnavailableWhenOllamaIsUnreachable() throws Exception {
        // application-test.yml points ollama.base-url at an unused local port, so this
        // exercises the real graceful-degradation path end-to-end (no mocking).
        String id = createSession();
        String body = objectMapper.writeValueAsString(Map.of("content", "please help"));
        mockMvc.perform(post("/api/sessions/" + id + "/messages").contentType("application/json").content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("ollama_unavailable"));

        // the session should be left in a consistent, inspectable FAILED state, not stuck
        mockMvc.perform(get("/api/sessions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.messages[?(@.role=='SYSTEM')]").exists());
    }

    @Test
    void endSession_generatesSummaryEvenWhenOllamaIsUnreachable() throws Exception {
        String id = createSession();
        mockMvc.perform(post("/api/sessions/" + id + "/end"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.narrativeAvailable").value(false))
                .andExpect(jsonPath("$.filesInspected").isArray())
                .andExpect(jsonPath("$.processingSummary.finalStatus").value("COMPLETED"));
    }

    @Test
    void getSummary_generatesOnDemandIfNotAlreadyStored() throws Exception {
        String id = createSession();
        mockMvc.perform(get("/api/sessions/" + id + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingSummary.turnCount").value(0));
    }
}
