package com.codecafe.aicodingagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_change_record")
public class FileChangeRecord {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private CodingSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tool_call_id")
    private ToolCall toolCall;

    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 10)
    private FileChangeType changeType;

    @Column(name = "backup_file_path", length = 1024)
    private String backupFilePath;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FileChangeRecord() {
        // for JPA
    }

    public FileChangeRecord(CodingSession session, ToolCall toolCall, String filePath,
                             FileChangeType changeType, String backupFilePath) {
        this.id = UUID.randomUUID().toString();
        this.session = session;
        this.toolCall = toolCall;
        this.filePath = filePath;
        this.changeType = changeType;
        this.backupFilePath = backupFilePath;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public CodingSession getSession() {
        return session;
    }

    public ToolCall getToolCall() {
        return toolCall;
    }

    public String getFilePath() {
        return filePath;
    }

    public FileChangeType getChangeType() {
        return changeType;
    }

    public String getBackupFilePath() {
        return backupFilePath;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
