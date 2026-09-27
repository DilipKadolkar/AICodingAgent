package com.codecafe.aicodingagent.service;

import com.codecafe.aicodingagent.config.AgentProperties;
import com.codecafe.aicodingagent.domain.CodingSession;
import com.codecafe.aicodingagent.domain.FileChangeRecord;
import com.codecafe.aicodingagent.domain.FileChangeType;
import com.codecafe.aicodingagent.domain.MessageRole;
import com.codecafe.aicodingagent.domain.SessionMessage;
import com.codecafe.aicodingagent.domain.SessionStatus;
import com.codecafe.aicodingagent.domain.ToolCall;
import com.codecafe.aicodingagent.domain.ToolName;
import com.codecafe.aicodingagent.exception.InvalidRequestException;
import com.codecafe.aicodingagent.exception.OllamaException;
import com.codecafe.aicodingagent.exception.SessionBusyException;
import com.codecafe.aicodingagent.exception.SessionNotFoundException;
import com.codecafe.aicodingagent.repository.CodingSessionRepository;
import com.codecafe.aicodingagent.repository.FileChangeRecordRepository;
import com.codecafe.aicodingagent.repository.SessionMessageRepository;
import com.codecafe.aicodingagent.repository.ToolCallRepository;
import com.codecafe.aicodingagent.tool.CodeAgentTool;
import com.codecafe.aicodingagent.tool.ToolContext;
import com.codecafe.aicodingagent.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ties the Ollama client and the tool set together into a working coding
 * agent loop, driven by a CodingSession's persisted transcript.
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private static final Pattern TOOL_CALL_PATTERN =
            Pattern.compile("```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```");

    private static final Set<ToolName> EXPLORATION_TOOLS = EnumSet.of(
            ToolName.LIST_FILES, ToolName.SEARCH_CODE, ToolName.READ_FILE,
            ToolName.ANALYZE_STRUCTURE, ToolName.IDENTIFY_RELEVANT_FILES);

    private static final int MAX_HISTORY_MESSAGES = 40;
    private static final int MAX_HISTORY_CHARS = 16_000;

    private final CodingSessionRepository sessionRepository;
    private final SessionMessageRepository messageRepository;
    private final ToolCallRepository toolCallRepository;
    private final FileChangeRecordRepository fileChangeRecordRepository;
    private final OllamaClientService ollamaClientService;
    private final AgentProperties agentProperties;
    private final ObjectMapper objectMapper;
    private final Map<ToolName, CodeAgentTool> toolsByName;
    private final Map<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    public AgentOrchestrator(CodingSessionRepository sessionRepository,
                              SessionMessageRepository messageRepository,
                              ToolCallRepository toolCallRepository,
                              FileChangeRecordRepository fileChangeRecordRepository,
                              OllamaClientService ollamaClientService,
                              AgentProperties agentProperties,
                              ObjectMapper objectMapper,
                              List<CodeAgentTool> tools) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.toolCallRepository = toolCallRepository;
        this.fileChangeRecordRepository = fileChangeRecordRepository;
        this.ollamaClientService = ollamaClientService;
        this.agentProperties = agentProperties;
        this.objectMapper = objectMapper;
        this.toolsByName = tools.stream().collect(java.util.stream.Collectors.toMap(CodeAgentTool::name, t -> t));
    }

    public CodingSession handleUserMessage(String sessionId, String content) {
        if (content == null || content.isBlank()) {
            throw new InvalidRequestException("Message content must not be empty.");
        }
        String trimmed = content.trim();
        if (trimmed.length() > agentProperties.maxMessageLength()) {
            throw new InvalidRequestException(
                    "Message is too long (max " + agentProperties.maxMessageLength() + " characters).");
        }

        CodingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        if (session.isBusy()) {
            throw new SessionBusyException(sessionId);
        }

        ReentrantLock lock = sessionLocks.computeIfAbsent(sessionId, id -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new SessionBusyException(sessionId);
        }
        try {
            return runTurn(session, trimmed);
        } finally {
            lock.unlock();
        }
    }

    // Deliberately NOT @Transactional: each persistMessage/toolCallRepository.save call
    // below commits independently (Spring Data repositories are transactional per method),
    // so a crash mid-turn loses at most the in-flight step rather than the whole turn.
    private CodingSession runTurn(CodingSession session, String userContent) {
        String sessionId = session.getId();
        persistMessage(session, MessageRole.USER, userContent);
        session.setStatus(SessionStatus.PROCESSING);
        sessionRepository.save(session);

        try {
            Path repositoryRoot = Path.of(session.getRepositoryPath());
            List<ChatMessage> conversation = buildConversation(sessionId);

            for (int iteration = 1; iteration <= agentProperties.maxToolIterations(); iteration++) {
                String reply = ollamaClientService.chat(conversation);
                Matcher matcher = TOOL_CALL_PATTERN.matcher(reply);

                if (!matcher.find()) {
                    persistMessage(session, MessageRole.AGENT, reply.trim());
                    session.setStatus(SessionStatus.IDLE);
                    return sessionRepository.save(session);
                }

                conversation.add(ChatMessage.assistant(reply));

                JsonNode toolRequest;
                try {
                    toolRequest = objectMapper.readTree(matcher.group(1));
                } catch (Exception e) {
                    String nudge = "Your last message could not be parsed as a valid tool call JSON: "
                            + e.getMessage() + ". Please retry with a valid ```json {\"tool\": ..., \"params\": {...}} ``` block, or answer in plain text.";
                    conversation.add(ChatMessage.system(nudge));
                    continue;
                }

                String toolNameRaw = toolRequest.path("tool").asText("");
                ToolName toolName;
                try {
                    toolName = ToolName.valueOf(toolNameRaw.toUpperCase());
                } catch (IllegalArgumentException e) {
                    conversation.add(ChatMessage.system("Unknown tool '" + toolNameRaw
                            + "'. Available tools: " + java.util.Arrays.toString(ToolName.values())));
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> params = toolRequest.has("params")
                        ? objectMapper.convertValue(toolRequest.get("params"), Map.class)
                        : Map.of();

                if (toolName.isMutating() && !toolCallRepository.existsBySession_IdAndToolNameIn(sessionId, EXPLORATION_TOOLS)) {
                    String nudge = "You must explore this repository first (LIST_FILES, SEARCH_CODE, READ_FILE, "
                            + "ANALYZE_STRUCTURE, or IDENTIFY_RELEVANT_FILES) before using " + toolName
                            + ". Please explore, then retry the change.";
                    persistMessage(session, MessageRole.SYSTEM, nudge);
                    conversation.add(ChatMessage.system(nudge));
                    continue;
                }

                session.setStatus(SessionStatus.EXECUTING);
                sessionRepository.save(session);

                ToolCall toolCall = executeAndRecordTool(session, repositoryRoot, toolName, params);
                conversation.add(ChatMessage.system(summarizeForModel(toolCall)));

                session.setStatus(SessionStatus.PROCESSING);
                sessionRepository.save(session);
            }

            String limitMessage = "I reached the maximum number of tool calls (" + agentProperties.maxToolIterations()
                    + ") allowed for a single request without producing a final answer. "
                    + "Please refine your request, or send another message to continue.";
            persistMessage(session, MessageRole.AGENT, limitMessage);
            session.setStatus(SessionStatus.IDLE);
            return sessionRepository.save(session);

        } catch (Exception e) {
            log.error("Session {} turn failed", sessionId, e);
            persistMessage(session, MessageRole.SYSTEM, friendlyMessage(e));
            session.setStatus(SessionStatus.FAILED);
            sessionRepository.save(session);
            throw e;
        }
    }

    private ToolCall executeAndRecordTool(CodingSession session, Path repositoryRoot,
                                           ToolName toolName, Map<String, Object> params) {
        String inputJson = toJson(params);
        ToolCall toolCall = new ToolCall(session, toolName, inputJson);
        toolCall = toolCallRepository.save(toolCall);

        CodeAgentTool tool = toolsByName.get(toolName);
        ToolExecutionResult result;
        try {
            if (tool == null) {
                result = ToolExecutionResult.failure("Tool not implemented: " + toolName);
            } else {
                result = tool.execute(new ToolContext(session, repositoryRoot), params);
            }
        } catch (Exception e) {
            log.warn("Tool {} threw an exception", toolName, e);
            result = ToolExecutionResult.failure("Tool crashed unexpectedly: " + e.getMessage());
        }

        if (result.success()) {
            Map<String, Object> outputData = result.diff() != null
                    ? mergeWithDiff(result.data(), result.diff())
                    : result.data();
            toolCall.succeed(toJson(outputData));
            if (result.backupPath() != null) {
                toolCall.setBackupPath(result.backupPath());
            }
            recordFileChangeIfApplicable(session, toolCall, result);
        } else {
            toolCall.fail(result.errorMessage());
        }
        return toolCallRepository.save(toolCall);
    }

    private Map<String, Object> mergeWithDiff(Map<String, Object> data, String diff) {
        Map<String, Object> merged = new java.util.LinkedHashMap<>(data);
        merged.put("diff", diff);
        return merged;
    }

    private void recordFileChangeIfApplicable(CodingSession session, ToolCall toolCall, ToolExecutionResult result) {
        Object changeTypeObj = result.data().get("changeType");
        Object pathObj = result.data().get("path");
        if (changeTypeObj == null || pathObj == null) {
            return;
        }
        FileChangeType changeType = FileChangeType.valueOf(String.valueOf(changeTypeObj));
        FileChangeRecord record = new FileChangeRecord(session, toolCall, String.valueOf(pathObj),
                changeType, result.backupPath());
        fileChangeRecordRepository.save(record);
    }

    private String summarizeForModel(ToolCall toolCall) {
        String base = "Tool " + toolCall.getToolName() + " result: ";
        String body = toolCall.getStatus().name().equals("SUCCEEDED")
                ? truncate(toolCall.getOutputResult(), agentProperties.maxToolOutputLength())
                : "ERROR: " + toolCall.getErrorMessage();
        return base + body;
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "... (truncated)" : s;
    }

    private List<ChatMessage> buildConversation(String sessionId) {
        List<SessionMessage> history = messageRepository.findBySession_IdOrderBySequenceNumberAsc(sessionId);
        List<ChatMessage> truncated = truncateHistory(history);

        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(ChatMessage.system(OllamaClientService.SYSTEM_PROMPT));
        conversation.addAll(truncated);
        return conversation;
    }

    private List<ChatMessage> truncateHistory(List<SessionMessage> history) {
        List<ChatMessage> mapped = history.stream()
                .map(m -> new ChatMessage(toOllamaRole(m.getRole()), m.getContent()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        // Keep the most recent messages within the message-count and character budgets,
        // preserving conversational order, so very long sessions remain usable.
        while (mapped.size() > MAX_HISTORY_MESSAGES) {
            mapped.remove(0);
        }
        int totalChars = mapped.stream().mapToInt(m -> m.content().length()).sum();
        while (totalChars > MAX_HISTORY_CHARS && mapped.size() > 1) {
            ChatMessage removed = mapped.remove(0);
            totalChars -= removed.content().length();
        }
        return mapped;
    }

    private String toOllamaRole(MessageRole role) {
        return switch (role) {
            case USER -> "user";
            case AGENT -> "assistant";
            case SYSTEM -> "system";
        };
    }

    private void persistMessage(CodingSession session, MessageRole role, String content) {
        long sequence = messageRepository.countBySession_Id(session.getId());
        SessionMessage message = new SessionMessage(session, role, content, (int) sequence);
        messageRepository.save(message);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String friendlyMessage(Exception e) {
        if (e instanceof OllamaException) {
            return e.getMessage();
        }
        return "An unexpected error occurred while processing your request: " + e.getMessage()
                + ". Please try again; if this keeps happening, check the server logs.";
    }
}
