package com.codecafe.aicodingagent.tool;

import java.util.Map;

/** Structured outcome of running a single tool, shown to the user and fed back to the model. */
public record ToolExecutionResult(
        boolean success,
        String summary,
        Map<String, Object> data,
        String diff,
        String backupPath,
        String errorMessage) {

    public static ToolExecutionResult ok(String summary, Map<String, Object> data) {
        return new ToolExecutionResult(true, summary, data, null, null, null);
    }

    public static ToolExecutionResult okWithDiff(String summary, Map<String, Object> data,
                                                  String diff, String backupPath) {
        return new ToolExecutionResult(true, summary, data, diff, backupPath, null);
    }

    public static ToolExecutionResult failure(String errorMessage) {
        return new ToolExecutionResult(false, null, Map.of(), null, null, errorMessage);
    }
}
