package com.codecafe.aicodingagent.tool;

import com.codecafe.aicodingagent.domain.ToolName;
import com.codecafe.aicodingagent.util.DiffUtil;
import com.codecafe.aicodingagent.util.PathSafety;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Component
public class CreateFileTool implements CodeAgentTool {

    @Override
    public ToolName name() {
        return ToolName.CREATE_FILE;
    }

    @Override
    public ToolExecutionResult execute(ToolContext context, Map<String, Object> params) {
        Object pathObj = params.get("path");
        if (pathObj == null || String.valueOf(pathObj).isBlank()) {
            return ToolExecutionResult.failure("A non-empty 'path' parameter is required.");
        }
        String relativePath = String.valueOf(pathObj);
        String content = String.valueOf(params.getOrDefault("content", ""));

        Path file = PathSafety.resolveWithinRoot(context.repositoryRoot(), relativePath);
        if (Files.exists(file)) {
            return ToolExecutionResult.failure(
                    "File already exists: " + relativePath + ". Use MODIFY_FILE or REPLACE_CODE_SECTION instead.");
        }

        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, content);
        } catch (IOException e) {
            return ToolExecutionResult.failure("Failed to create file: " + e.getMessage());
        }

        String diff = DiffUtil.unifiedDiff("", content, relativePath);
        String summary = "Created " + relativePath + " (" + content.lines().count() + " lines).";
        return ToolExecutionResult.okWithDiff(summary, Map.of(
                "path", relativePath,
                "changeType", "CREATED"
        ), diff, null);
    }
}
