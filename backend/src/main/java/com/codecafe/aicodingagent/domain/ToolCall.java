package com.codecafe.aicodingagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tool_call")
public class ToolCall {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private CodingSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id")
    private SessionMessage message;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool_name", nullable = false, length = 40)
    private ToolName toolName;

    @Lob
    @Column(name = "input_params", columnDefinition = "LONGTEXT")
    private String inputParams;

    @Lob
    @Column(name = "output_result", columnDefinition = "LONGTEXT")
    private String outputResult;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ToolCallStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "backup_path", length = 1024)
    private String backupPath;

    @Lob
    @Column(name = "error_message", columnDefinition = "LONGTEXT")
    private String errorMessage;

    protected ToolCall() {
        // for JPA
    }

    public ToolCall(CodingSession session, ToolName toolName, String inputParams) {
        this.id = UUID.randomUUID().toString();
        this.session = session;
        this.toolName = toolName;
        this.inputParams = inputParams;
        this.status = ToolCallStatus.STARTED;
        this.startedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    public void succeed(String outputResult) {
        this.status = ToolCallStatus.SUCCEEDED;
        this.outputResult = outputResult;
        this.finishedAt = Instant.now();
    }

    public void fail(String errorMessage) {
        this.status = ToolCallStatus.FAILED;
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public CodingSession getSession() {
        return session;
    }

    public SessionMessage getMessage() {
        return message;
    }

    public void setMessage(SessionMessage message) {
        this.message = message;
    }

    public ToolName getToolName() {
        return toolName;
    }

    public String getInputParams() {
        return inputParams;
    }

    public String getOutputResult() {
        return outputResult;
    }

    public ToolCallStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getBackupPath() {
        return backupPath;
    }

    public void setBackupPath(String backupPath) {
        this.backupPath = backupPath;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
