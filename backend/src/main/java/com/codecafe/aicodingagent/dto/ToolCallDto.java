package com.codecafe.aicodingagent.dto;

import com.codecafe.aicodingagent.domain.ToolCall;

import java.time.Instant;

public record ToolCallDto(
        String id, String toolName, String inputParams, String outputResult, String status,
        Instant startedAt, Instant finishedAt, String backupPath, String errorMessage) {

    public static ToolCallDto from(ToolCall t) {
        return new ToolCallDto(
                t.getId(), t.getToolName().name(), t.getInputParams(), t.getOutputResult(),
                t.getStatus().name(), t.getStartedAt(), t.getFinishedAt(), t.getBackupPath(), t.getErrorMessage());
    }
}
