package com.codecafe.aicodingagent.dto;

import java.util.List;
import java.util.Map;

public record SessionSummaryResponse(
        String narrative,
        boolean narrativeAvailable,
        List<String> filesInspected,
        List<FileChangeDto> filesModified,
        List<CommandExecutionDto> commandsExecuted,
        List<VerificationResultDto> verificationResults,
        ProcessingSummaryDto processingSummary) {

    public record FileChangeDto(String path, String changeType, String backupPath) {
    }

    public record CommandExecutionDto(String command, Integer exitCode, String output,
                                       Long durationMillis, boolean verification) {
    }

    public record VerificationResultDto(String command, boolean passed, Integer exitCode, Long durationMillis) {
    }

    public record ProcessingSummaryDto(long durationMillis, int turnCount,
                                        Map<String, Long> toolCallCountsByType, String finalStatus) {
    }
}
