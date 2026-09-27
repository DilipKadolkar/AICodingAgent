package com.codecafe.aicodingagent.service;

import com.codecafe.aicodingagent.domain.CodingSession;
import com.codecafe.aicodingagent.domain.MessageRole;
import com.codecafe.aicodingagent.domain.SessionStatus;
import com.codecafe.aicodingagent.domain.ToolCallStatus;
import com.codecafe.aicodingagent.domain.ToolName;
import com.codecafe.aicodingagent.exception.SessionBusyException;
import com.codecafe.aicodingagent.repository.CodingSessionRepository;
import com.codecafe.aicodingagent.repository.SessionMessageRepository;
import com.codecafe.aicodingagent.repository.ToolCallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class AgentOrchestratorTest {

    @Autowired
    private AgentOrchestrator orchestrator;
    @Autowired
    private CodingSessionRepository sessionRepository;
    @Autowired
    private SessionMessageRepository messageRepository;
    @Autowired
    private ToolCallRepository toolCallRepository;
    @MockBean
    private OllamaClientService ollamaClientService;

    @TempDir
    Path repoRoot;

    private CodingSession session;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(repoRoot.resolve("README.md"), "hello world");
        session = sessionRepository.save(new CodingSession("test session", repoRoot.toString()));
    }

    @Test
    void exploreThenAnswer_persistsToolCallAndFinalMessage() {
        when(ollamaClientService.chat(any()))
                .thenReturn("```json\n{\"tool\": \"LIST_FILES\", \"params\": {\"path\": \"\"}}\n```")
                .thenReturn("I looked at the repository; it contains a single README.md file.");

        CodingSession updated = orchestrator.handleUserMessage(session.getId(), "What's in this repo?");

        assertThat(updated.getStatus()).isEqualTo(SessionStatus.IDLE);

        var toolCalls = toolCallRepository.findBySession_IdOrderByStartedAtAsc(session.getId());
        assertThat(toolCalls).hasSize(1);
        assertThat(toolCalls.get(0).getToolName()).isEqualTo(ToolName.LIST_FILES);
        assertThat(toolCalls.get(0).getStatus()).isEqualTo(ToolCallStatus.SUCCEEDED);

        var messages = messageRepository.findBySession_IdOrderBySequenceNumberAsc(session.getId());
        assertThat(messages).extracting("role").contains(MessageRole.USER, MessageRole.AGENT);
        assertThat(messages.get(messages.size() - 1).getContent()).contains("README.md");
    }

    @Test
    void mutatingToolWithoutPriorExploration_isNudgedInsteadOfExecuted() {
        when(ollamaClientService.chat(any()))
                .thenReturn("```json\n{\"tool\": \"MODIFY_FILE\", \"params\": {\"path\": \"README.md\", \"content\": \"x\"}}\n```")
                .thenReturn("Understood, I'll explore first.");

        orchestrator.handleUserMessage(session.getId(), "Change the readme");

        var toolCalls = toolCallRepository.findBySession_IdOrderByStartedAtAsc(session.getId());
        assertThat(toolCalls).isEmpty(); // MODIFY_FILE was never actually executed

        var messages = messageRepository.findBySession_IdOrderBySequenceNumberAsc(session.getId());
        assertThat(messages).extracting("role").contains(MessageRole.SYSTEM);
        assertThat(messages.stream().anyMatch(m -> m.getContent().toLowerCase().contains("explore")))
                .isTrue();
    }

    @Test
    void reachingMaxIterations_stopsGracefullyWithExplanatoryMessage() {
        when(ollamaClientService.chat(any()))
                .thenReturn("```json\n{\"tool\": \"LIST_FILES\", \"params\": {\"path\": \"\"}}\n```");

        CodingSession updated = orchestrator.handleUserMessage(session.getId(), "Keep exploring forever");

        assertThat(updated.getStatus()).isEqualTo(SessionStatus.IDLE);
        var toolCalls = toolCallRepository.findBySession_IdOrderByStartedAtAsc(session.getId());
        assertThat(toolCalls).hasSize(5); // test profile: agent.max-tool-iterations=5

        var messages = messageRepository.findBySession_IdOrderBySequenceNumberAsc(session.getId());
        String lastAgentMessage = messages.get(messages.size() - 1).getContent();
        assertThat(lastAgentMessage).containsIgnoringCase("maximum number of tool calls");
    }

    @Test
    void rejectsEmptyMessage() {
        assertThatThrownBy(() -> orchestrator.handleUserMessage(session.getId(), "   "))
                .isInstanceOf(com.codecafe.aicodingagent.exception.InvalidRequestException.class);
    }

    @Test
    void rejectsConcurrentMessageOnABusySession() {
        session.setStatus(SessionStatus.PROCESSING);
        sessionRepository.save(session);

        assertThatThrownBy(() -> orchestrator.handleUserMessage(session.getId(), "another request"))
                .isInstanceOf(SessionBusyException.class);
    }
}
