package com.codecafe.aicodingagent.service;

import com.codecafe.aicodingagent.domain.CodingSession;
import com.codecafe.aicodingagent.domain.FileChangeRecord;
import com.codecafe.aicodingagent.domain.FileChangeType;
import com.codecafe.aicodingagent.domain.ToolCall;
import com.codecafe.aicodingagent.domain.ToolName;
import com.codecafe.aicodingagent.dto.SessionSummaryResponse;
import com.codecafe.aicodingagent.repository.CodingSessionRepository;
import com.codecafe.aicodingagent.repository.FileChangeRecordRepository;
import com.codecafe.aicodingagent.repository.ToolCallRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class SessionSummaryServiceTest {

    @Autowired
    private SessionSummaryService summaryService;
    @Autowired
    private CodingSessionRepository sessionRepository;
    @Autowired
    private ToolCallRepository toolCallRepository;
    @Autowired
    private FileChangeRecordRepository fileChangeRecordRepository;
    @MockBean
    private OllamaClientService ollamaClientService;

    @Test
    void summarizesInspectedFilesModifiedFilesCommandsAndVerification() {
        CodingSession session = sessionRepository.save(new CodingSession("s", "/tmp/repo"));

        ToolCall listFiles = new ToolCall(session, ToolName.LIST_FILES, "{}");
        listFiles.succeed("{\"entries\":[{\"path\":\"README.md\",\"type\":\"file\"}]}");
        toolCallRepository.save(listFiles);

        ToolCall modify = new ToolCall(session, ToolName.MODIFY_FILE, "{}");
        modify.succeed("{\"path\":\"README.md\",\"changeType\":\"MODIFIED\"}");
        toolCallRepository.save(modify);
        fileChangeRecordRepository.save(new FileChangeRecord(session, modify, "README.md",
                FileChangeType.MODIFIED, "/tmp/backup/README.md"));

        ToolCall verify = new ToolCall(session, ToolName.VERIFY_CHANGES, "{}");
        verify.succeed("{\"command\":\"mvn test\",\"passed\":true,\"exitCode\":0,\"durationMillis\":1234,\"output\":\"OK\"}");
        toolCallRepository.save(verify);

        when(ollamaClientService.chat(any())).thenReturn("The agent inspected README.md and updated it.");

        SessionSummaryResponse summary = summaryService.generateSummary(session.getId(), false);

        assertThat(summary.filesInspected()).contains("README.md");
        assertThat(summary.filesModified()).hasSize(1);
        assertThat(summary.filesModified().get(0).changeType()).isEqualTo("MODIFIED");
        assertThat(summary.commandsExecuted()).hasSize(1);
        assertThat(summary.verificationResults()).hasSize(1);
        assertThat(summary.verificationResults().get(0).passed()).isTrue();
        assertThat(summary.processingSummary().toolCallCountsByType())
                .containsEntry("LIST_FILES", 1L)
                .containsEntry("MODIFY_FILE", 1L)
                .containsEntry("VERIFY_CHANGES", 1L);
        assertThat(summary.narrativeAvailable()).isTrue();
        assertThat(summary.narrative()).contains("README.md");
    }

    @Test
    void fallsBackGracefullyWhenOllamaIsUnavailableForNarrative() {
        CodingSession session = sessionRepository.save(new CodingSession("s2", "/tmp/repo2"));
        when(ollamaClientService.chat(any()))
                .thenThrow(new com.codecafe.aicodingagent.exception.OllamaException.ConnectionFailed("http://x", null));

        SessionSummaryResponse summary = summaryService.generateSummary(session.getId(), false);

        assertThat(summary.narrativeAvailable()).isFalse();
        assertThat(summary.narrative()).isNotBlank();
        assertThat(summary.filesInspected()).isEmpty();
        assertThat(summary.processingSummary()).isNotNull();
    }
}
