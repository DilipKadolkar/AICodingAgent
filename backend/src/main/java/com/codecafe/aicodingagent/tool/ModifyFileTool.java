package com.codecafe.aicodingagent.tool;

import com.codecafe.aicodingagent.domain.ToolName;
import com.codecafe.aicodingagent.service.BackupRestoreService;
import com.codecafe.aicodingagent.util.DiffUtil;
import com.codecafe.aicodingagent.util.PathSafety;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Component
public class ModifyFileTool implements CodeAgentTool {

    private final BackupRestoreService backupRestoreService;

    public ModifyFileTool(BackupRestoreService backupRestoreService) {
        this.backupRestoreService = backupRestoreService;
    }

    @Override
    public ToolName name() {
        return ToolName.MODIFY_FILE;
    }

    @Override
    public ToolExecutionResult execute(ToolContext context, Map<String, Object> params) {
        Object pathObj = params.get("path");
        if (pathObj == null || String.valueOf(pathObj).isBlank()) {
            return ToolExecutionResult.failure("A non-empty 'path' parameter is required.");
        }
        String relativePath = String.valueOf(pathObj);
        String newContent = String.valueOf(params.getOrDefault("content", ""));

        Path file = PathSafety.resolveWithinRoot(context.repositoryRoot(), relativePath);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            return ToolExecutionResult.failure(
                    "File does not exist: " + relativePath + ". Use CREATE_FILE instead.");
        }

        String oldContent;
        try {
            oldContent = Files.readString(file);
        } catch (IOException e) {
            return ToolExecutionResult.failure("Could not read existing file: " + e.getMessage());
        }

        Path backupPath;
        try {
            backupPath = backupRestoreService.backup(context.repositoryRoot(),
                    context.session().getId(), file);
        } catch (IOException e) {
            return ToolExecutionResult.failure("Failed to create a backup before modifying: " + e.getMessage());
        }

        try {
            Files.writeString(file, newContent);
        } catch (IOException e) {
            return ToolExecutionResult.failure(
                    "Failed to write new content (original file is unchanged; backup preserved at "
                            + backupPath + "): " + e.getMessage());
        }

        String diff = DiffUtil.unifiedDiff(oldContent, newContent, relativePath);
        String summary = "Modified " + relativePath + " (backup saved).";
        return ToolExecutionResult.okWithDiff(summary, Map.of(
                "path", relativePath,
                "changeType", "MODIFIED"
        ), diff, backupPath.toString());
    }
}
