package com.codecafe.aicodingagent.tool;

import com.codecafe.aicodingagent.domain.ToolName;
import com.codecafe.aicodingagent.util.PathSafety;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
public class ReadFileTool implements CodeAgentTool {

    private static final int DEFAULT_MAX_LINES = 500;

    @Override
    public ToolName name() {
        return ToolName.READ_FILE;
    }

    @Override
    public ToolExecutionResult execute(ToolContext context, Map<String, Object> params) {
        Object pathObj = params.get("path");
        if (pathObj == null || String.valueOf(pathObj).isBlank()) {
            return ToolExecutionResult.failure("A non-empty 'path' parameter is required.");
        }
        String relativePath = String.valueOf(pathObj);
        Path file = PathSafety.resolveWithinRoot(context.repositoryRoot(), relativePath);

        if (!Files.exists(file)) {
            return ToolExecutionResult.failure("File does not exist: " + relativePath);
        }
        if (Files.isDirectory(file)) {
            return ToolExecutionResult.failure("Path is a directory, not a file: " + relativePath);
        }

        int maxLines = intParam(params, "maxLines", DEFAULT_MAX_LINES);
        Integer startLine = params.get("startLine") == null ? null : intParam(params, "startLine", 1);
        Integer endLine = params.get("endLine") == null ? null : intParam(params, "endLine", -1);

        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            return ToolExecutionResult.failure("Could not read file (is it binary?): " + e.getMessage());
        }

        int from = startLine != null ? Math.max(1, startLine) - 1 : 0;
        int to = endLine != null ? Math.min(lines.size(), endLine) : Math.min(lines.size(), from + maxLines);
        if (from >= lines.size()) {
            return ToolExecutionResult.failure("startLine is beyond the end of the file "
                    + "(file has " + lines.size() + " lines).");
        }
        boolean truncated = to < lines.size() && endLine == null;
        List<String> slice = lines.subList(from, Math.max(from, to));
        String content = String.join("\n", slice);

        String summary = "Read " + slice.size() + " line" + (slice.size() == 1 ? "" : "s")
                + " from " + relativePath + (truncated ? " (truncated)" : "");
        return ToolExecutionResult.ok(summary, Map.of(
                "path", relativePath,
                "startLine", from + 1,
                "endLine", from + slice.size(),
                "totalLines", lines.size(),
                "truncated", truncated,
                "content", content
        ));
    }

    private int intParam(Map<String, Object> params, String key, int fallback) {
        Object v = params.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
