package com.codecafe.aicodingagent.service;

import com.codecafe.aicodingagent.domain.CodingSession;
import com.codecafe.aicodingagent.domain.FileChangeRecord;
import com.codecafe.aicodingagent.domain.MessageRole;
import com.codecafe.aicodingagent.domain.ToolCall;
import com.codecafe.aicodingagent.domain.ToolName;
import com.codecafe.aicodingagent.dto.SessionSummaryResponse;
import com.codecafe.aicodingagent.dto.SessionSummaryResponse.CommandExecutionDto;
import com.codecafe.aicodingagent.dto.SessionSummaryResponse.FileChangeDto;
import com.codecafe.aicodingagent.dto.SessionSummaryResponse.ProcessingSummaryDto;
import com.codecafe.aicodingagent.dto.SessionSummaryResponse.VerificationResultDto;
import com.codecafe.aicodingagent.exception.OllamaException;
import com.codecafe.aicodingagent.exception.SessionNotFoundException;
import com.codecafe.aicodingagent.repository.CodingSessionRepository;
import com.codecafe.aicodingagent.repository.FileChangeRecordRepository;
import com.codecafe.aicodingagent.repository.SessionMessageRepository;
import com.codecafe.aicodingagent.repository.ToolCallRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SessionSummaryService {

    private static final Logger log = LoggerFactory.getLogger(SessionSummaryService.class);

    private final CodingSessionRepository sessionRepository;
    private final SessionMessageRepository messageRepository;
    private final ToolCallRepository toolCallRepository;
    private final FileChangeRecordRepository fileChangeRecordRepository;
    private final OllamaClientService ollamaClientService;
    private final ObjectMapper objectMapper;

    public SessionSummaryService(CodingSessionRepository sessionRepository,
                                  SessionMessageRepository messageRepository,
                                  ToolCallRepository toolCallRepository,
                                  FileChangeRecordRepository fileChangeRecordRepository,
                                  OllamaClientService ollamaClientService,
                                  ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.toolCallRepository = toolCallRepository;
        this.fileChangeRecordRepository = fileChangeRecordRepository;
        this.ollamaClientService = ollamaClientService;
        this.objectMapper = objectMapper;
    }

    public SessionSummaryResponse generateSummary(String sessionId, boolean persist) {
        CodingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        List<ToolCall> toolCalls = toolCallRepository.findBySession_IdOrderByStartedAtAsc(sessionId);
        List<FileChangeRecord> fileChanges = fileChangeRecordRepository.findBySession_IdOrderByCreatedAtAsc(sessionId);
        long turnCount = messageRepository.findBySession_IdOrderBySequenceNumberAsc(sessionId).stream()
                .filter(m -> m.getRole() == MessageRole.USER)
                .count();

        List<String> filesInspected = extractInspectedFiles(toolCalls);
        List<FileChangeDto> filesModified = extractFilesModified(fileChanges);
        List<CommandExecutionDto> commandsExecuted = extractCommands(toolCalls);
        List<VerificationResultDto> verificationResults = extractVerifications(toolCalls);
        ProcessingSummaryDto processingSummary = buildProcessingSummary(session, toolCalls, (int) turnCount);

        String narrative;
        boolean narrativeAvailable;
        try {
            narrative = generateNarrative(session, toolCalls);
            narrativeAvailable = true;
        } catch (OllamaException e) {
            log.warn("Could not generate AI narrative summary for session {}: {}", sessionId, e.getMessage());
            narrative = "AI-generated recap is unavailable right now because the local model could not be reached. "
                    + "The structured details below are still complete.";
            narrativeAvailable = false;
        }

        SessionSummaryResponse response = new SessionSummaryResponse(
                narrative, narrativeAvailable, filesInspected, filesModified,
                commandsExecuted, verificationResults, processingSummary);

        if (persist) {
            session.setSummaryText(narrative);
            session.setSummaryJson(toJson(response));
            sessionRepository.save(session);
        }
        return response;
    }

    public SessionSummaryResponse getStoredOrGenerate(String sessionId) {
        CodingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        if (session.getSummaryJson() != null) {
            try {
                return objectMapper.readValue(session.getSummaryJson(), SessionSummaryResponse.class);
            } catch (Exception e) {
                log.warn("Stored summary for session {} could not be parsed, regenerating", sessionId);
            }
        }
        return generateSummary(sessionId, false);
    }

    private String generateNarrative(CodingSession session, List<ToolCall> toolCalls) {
        StringBuilder transcript = new StringBuilder();
        transcript.append("Repository: ").append(session.getRepositoryPath()).append('\n');
        for (ToolCall tc : toolCalls) {
            transcript.append("- ").append(tc.getToolName()).append(": ").append(tc.getStatus()).append('\n');
        }
        String prompt = "Summarize in 3-5 sentences, in a professional and conversational tone, "
                + "what was accomplished in this coding assistant session, based on this list of actions taken:\n\n"
                + transcript;
        return ollamaClientService.chat(List.of(
                ChatMessage.system("You write short, factual session recaps for a developer tool. "
                        + "Only describe actions that are explicitly listed; never invent files or outcomes."),
                ChatMessage.user(prompt)
        )).trim();
    }

    private List<String> extractInspectedFiles(List<ToolCall> toolCalls) {
        Set<ToolName> explorationTools = Set.of(ToolName.LIST_FILES, ToolName.SEARCH_CODE,
                ToolName.READ_FILE, ToolName.ANALYZE_STRUCTURE, ToolName.IDENTIFY_RELEVANT_FILES);
        Set<String> paths = new LinkedHashSet<>();
        for (ToolCall tc : toolCalls) {
            if (!explorationTools.contains(tc.getToolName()) || tc.getOutputResult() == null) {
                continue;
            }
            collectPathStrings(parseJsonQuietly(tc.getOutputResult()), paths);
        }
        return new ArrayList<>(paths);
    }

    private void collectPathStrings(JsonNode node, Set<String> out) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (entry.getKey().equals("path") && entry.getValue().isTextual()) {
                    out.add(entry.getValue().asText());
                } else {
                    collectPathStrings(entry.getValue(), out);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectPathStrings(child, out));
        }
    }

    private List<FileChangeDto> extractFilesModified(List<FileChangeRecord> fileChanges) {
        Map<String, FileChangeDto> byPath = new LinkedHashMap<>();
        for (FileChangeRecord fc : fileChanges) {
            byPath.put(fc.getFilePath(), new FileChangeDto(
                    fc.getFilePath(), fc.getChangeType().name(), fc.getBackupFilePath()));
        }
        return new ArrayList<>(byPath.values());
    }

    private List<CommandExecutionDto> extractCommands(List<ToolCall> toolCalls) {
        List<CommandExecutionDto> commands = new ArrayList<>();
        for (ToolCall tc : toolCalls) {
            if (tc.getToolName() != ToolName.EXECUTE_COMMAND && tc.getToolName() != ToolName.VERIFY_CHANGES) {
                continue;
            }
            if (tc.getOutputResult() == null) {
                continue;
            }
            JsonNode node = parseJsonQuietly(tc.getOutputResult());
            String command = node.path("command").asText(null);
            if (command == null) {
                continue;
            }
            Integer exitCode = node.has("exitCode") ? node.get("exitCode").asInt() : null;
            String output = node.path("output").asText(null);
            Long duration = node.has("durationMillis") ? node.get("durationMillis").asLong() : null;
            commands.add(new CommandExecutionDto(command, exitCode, output, duration,
                    tc.getToolName() == ToolName.VERIFY_CHANGES));
        }
        return commands;
    }

    private List<VerificationResultDto> extractVerifications(List<ToolCall> toolCalls) {
        List<VerificationResultDto> results = new ArrayList<>();
        for (ToolCall tc : toolCalls) {
            if (tc.getToolName() != ToolName.VERIFY_CHANGES || tc.getOutputResult() == null) {
                continue;
            }
            JsonNode node = parseJsonQuietly(tc.getOutputResult());
            results.add(new VerificationResultDto(
                    node.path("command").asText(null),
                    node.path("passed").asBoolean(false),
                    node.has("exitCode") ? node.get("exitCode").asInt() : null,
                    node.has("durationMillis") ? node.get("durationMillis").asLong() : null
            ));
        }
        return results;
    }

    private ProcessingSummaryDto buildProcessingSummary(CodingSession session, List<ToolCall> toolCalls, int turnCount) {
        Instant end = session.getEndedAt() != null ? session.getEndedAt() : Instant.now();
        long durationMillis = ChronoUnit.MILLIS.between(session.getCreatedAt(), end);
        Map<String, Long> countsByType = new LinkedHashMap<>();
        for (ToolCall tc : toolCalls) {
            countsByType.merge(tc.getToolName().name(), 1L, Long::sum);
        }
        return new ProcessingSummaryDto(durationMillis, turnCount, countsByType, session.getStatus().name());
    }

    private JsonNode parseJsonQuietly(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.getNodeFactory().objectNode();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
