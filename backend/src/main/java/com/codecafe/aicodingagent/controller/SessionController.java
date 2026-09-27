package com.codecafe.aicodingagent.controller;

import com.codecafe.aicodingagent.config.AgentProperties;
import com.codecafe.aicodingagent.domain.CodingSession;
import com.codecafe.aicodingagent.domain.SessionStatus;
import com.codecafe.aicodingagent.dto.CreateSessionRequest;
import com.codecafe.aicodingagent.dto.MessageDto;
import com.codecafe.aicodingagent.dto.PostMessageRequest;
import com.codecafe.aicodingagent.dto.SessionDetailDto;
import com.codecafe.aicodingagent.dto.SessionListItemDto;
import com.codecafe.aicodingagent.dto.SessionSummaryResponse;
import com.codecafe.aicodingagent.dto.ToolCallDto;
import com.codecafe.aicodingagent.exception.InvalidRequestException;
import com.codecafe.aicodingagent.exception.SessionNotFoundException;
import com.codecafe.aicodingagent.repository.CodingSessionRepository;
import com.codecafe.aicodingagent.repository.SessionMessageRepository;
import com.codecafe.aicodingagent.repository.ToolCallRepository;
import com.codecafe.aicodingagent.service.AgentOrchestrator;
import com.codecafe.aicodingagent.service.SessionSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final CodingSessionRepository sessionRepository;
    private final SessionMessageRepository messageRepository;
    private final ToolCallRepository toolCallRepository;
    private final AgentOrchestrator orchestrator;
    private final SessionSummaryService summaryService;
    private final AgentProperties agentProperties;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    public SessionController(CodingSessionRepository sessionRepository,
                              SessionMessageRepository messageRepository,
                              ToolCallRepository toolCallRepository,
                              AgentOrchestrator orchestrator,
                              SessionSummaryService summaryService,
                              AgentProperties agentProperties) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.toolCallRepository = toolCallRepository;
        this.orchestrator = orchestrator;
        this.summaryService = summaryService;
        this.agentProperties = agentProperties;
    }

    @Operation(summary = "Start a new coding assistant session against a repository")
    @PostMapping
    public SessionListItemDto createSession(@Valid @RequestBody CreateSessionRequest request) {
        String repoPath = request.repositoryPath().trim();
        File dir = new File(repoPath);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new InvalidRequestException(
                    "repositoryPath does not exist or is not a directory: " + repoPath);
        }
        String allowedBase = agentProperties.allowedRepositoryBaseDir();
        if (allowedBase != null && !allowedBase.isBlank()) {
            String canonicalBase = canonicalPath(new File(allowedBase));
            String canonicalTarget = canonicalPath(dir);
            if (!canonicalTarget.equals(canonicalBase) && !canonicalTarget.startsWith(canonicalBase + File.separator)) {
                throw new InvalidRequestException(
                        "repositoryPath must be inside the configured allowed base directory: " + allowedBase);
            }
        }
        String title = (request.title() == null || request.title().isBlank())
                ? "Session on " + dir.getName()
                : request.title().trim();
        CodingSession session = new CodingSession(title, dir.getAbsolutePath());
        session = sessionRepository.save(session);
        return SessionListItemDto.from(session);
    }

    @Operation(summary = "List sessions, most recently updated first, for the resume screen")
    @GetMapping
    public List<SessionListItemDto> listSessions(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return sessionRepository.findAllByOrderByUpdatedAtDesc(PageRequest.of(page, size, Sort.unsorted()))
                .map(SessionListItemDto::from)
                .getContent();
    }

    @Operation(summary = "Get full session detail: transcript and tool calls, for resuming or reviewing")
    @GetMapping("/{id}")
    public SessionDetailDto getSession(@PathVariable String id) {
        return buildDetail(id);
    }

    @Operation(summary = "Submit a natural-language request to the agent for this session")
    @PostMapping("/{id}/messages")
    public SessionDetailDto postMessage(@PathVariable String id, @Valid @RequestBody PostMessageRequest request) {
        orchestrator.handleUserMessage(id, request.content());
        return buildDetail(id);
    }

    @Operation(summary = "Live status/tool-call/message updates for the in-flight request on this session")
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String id) {
        if (!sessionRepository.existsById(id)) {
            throw new SessionNotFoundException(id);
        }
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        sseExecutor.submit(() -> pollAndEmit(id, emitter));
        return emitter;
    }

    @PreDestroy
    void shutdown() {
        sseExecutor.shutdownNow();
    }

    private void pollAndEmit(String id, SseEmitter emitter) {
        try {
            String lastStatus = null;
            int lastMessageCount = -1;
            int lastToolCallCount = -1;
            long deadline = System.currentTimeMillis() + 5 * 60 * 1000L;
            boolean sawActivity = false;

            while (System.currentTimeMillis() < deadline) {
                CodingSession session = sessionRepository.findById(id).orElse(null);
                if (session == null) {
                    emitter.complete();
                    return;
                }
                String status = session.getStatus().name();
                int messageCount = messageRepository.findBySession_IdOrderBySequenceNumberAsc(id).size();
                int toolCallCount = toolCallRepository.findBySession_IdOrderByStartedAtAsc(id).size();

                if (!status.equals(lastStatus)) {
                    emitter.send(SseEmitter.event().name("status").data(status));
                    lastStatus = status;
                }
                if (messageCount != lastMessageCount || toolCallCount != lastToolCallCount) {
                    emitter.send(SseEmitter.event().name("update").data(buildDetail(id)));
                    lastMessageCount = messageCount;
                    lastToolCallCount = toolCallCount;
                }

                boolean busy = session.isBusy();
                sawActivity = sawActivity || busy;
                if (sawActivity && !busy) {
                    emitter.complete();
                    return;
                }
                Thread.sleep(400);
            }
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    @Operation(summary = "End the session, generating and persisting its summary")
    @PostMapping("/{id}/end")
    public SessionSummaryResponse endSession(@PathVariable String id) {
        CodingSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new SessionNotFoundException(id));
        if (session.getStatus() != SessionStatus.FAILED) {
            session.setStatus(SessionStatus.COMPLETED);
        }
        session.setEndedAt(Instant.now());
        sessionRepository.save(session);
        return summaryService.generateSummary(id, true);
    }

    @Operation(summary = "Get the session's summary, generating it on demand if not already stored")
    @GetMapping("/{id}/summary")
    public SessionSummaryResponse getSummary(@PathVariable String id) {
        return summaryService.getStoredOrGenerate(id);
    }

    private String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (java.io.IOException e) {
            return file.getAbsolutePath();
        }
    }

    private SessionDetailDto buildDetail(String id) {
        CodingSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new SessionNotFoundException(id));
        List<MessageDto> messages = messageRepository.findBySession_IdOrderBySequenceNumberAsc(id)
                .stream().map(MessageDto::from).toList();
        List<ToolCallDto> toolCalls = toolCallRepository.findBySession_IdOrderByStartedAtAsc(id)
                .stream().map(ToolCallDto::from).toList();
        return SessionDetailDto.from(session, messages, toolCalls);
    }
}
