package com.codecafe.aicodingagent.tool;

import com.codecafe.aicodingagent.domain.ToolName;
import com.codecafe.aicodingagent.service.BackupRestoreService;
import com.codecafe.aicodingagent.util.DiffUtil;
import com.codecafe.aicodingagent.util.PathSafety;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
public class ReplaceCodeSectionTool implements CodeAgentTool {

    private final BackupRestoreService backupRestoreService;

    public ReplaceCodeSectionTool(BackupRestoreService backupRestoreService) {
        this.backupRestoreService = backupRestoreService;
    }

    @Override
    public ToolName name() {
        return ToolName.REPLACE_CODE_SECTION;
    }

    @Override
    public ToolExecutionResult execute(ToolContext context, Map<String, Object> params) {
        Object pathObj = params.get("path");
        if (pathObj == null || String.valueOf(pathObj).isBlank()) {
            return ToolExecutionResult.failure("A non-empty 'path' parameter is required.");
        }
        String relativePath = String.valueOf(pathObj);
        Object newTextObj = params.get("newText");
        if (newTextObj == null) {
            return ToolExecutionResult.failure("A 'newText' parameter is required.");
        }
        String newText = String.valueOf(newTextObj);

        Path file = PathSafety.resolveWithinRoot(context.repositoryRoot(), relativePath);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            return ToolExecutionResult.failure("File does not exist: " + relativePath);
        }

        String oldContent;
        try {
            oldContent = Files.readString(file);
        } catch (IOException e) {
            return ToolExecutionResult.failure("Could not read file: " + e.getMessage());
        }

        String newContent;
        Object oldTextObj = params.get("oldText");
        if (oldTextObj != null && !String.valueOf(oldTextObj).isEmpty()) {
            String oldText = String.valueOf(oldTextObj);
            int occurrences = countOccurrences(oldContent, oldText);
            if (occurrences == 0) {
                return ToolExecutionResult.failure(
                        "No match found for the given text in " + relativePath + ". File left unchanged.");
            }
            if (occurrences > 1) {
                return ToolExecutionResult.failure(
                        "Ambiguous match: found " + occurrences + " occurrences of the given text in "
                                + relativePath + ". Make the target text unique. File left unchanged.");
            }
            int idx = oldContent.indexOf(oldText);
            newContent = oldContent.substring(0, idx) + newText + oldContent.substring(idx + oldText.length());
        } else if (params.get("startLine") != null && params.get("endLine") != null) {
            List<String> lines = new java.util.ArrayList<>(List.of(oldContent.split("\n", -1)));
            int startLine = intParam(params, "startLine");
            int endLine = intParam(params, "endLine");
            if (startLine < 1 || endLine < startLine || endLine > lines.size()) {
                return ToolExecutionResult.failure(
                        "Invalid line range [" + startLine + "," + endLine + "] for a file with "
                                + lines.size() + " lines. File left unchanged.");
            }
            List<String> before = lines.subList(0, startLine - 1);
            List<String> after = lines.subList(endLine, lines.size());
            StringBuilder sb = new StringBuilder();
            before.forEach(l -> sb.append(l).append('\n'));
            sb.append(newText);
            if (!newText.endsWith("\n") && !after.isEmpty()) {
                sb.append('\n');
            }
            after.forEach(l -> sb.append(l).append('\n'));
            newContent = sb.toString();
        } else {
            return ToolExecutionResult.failure(
                    "Provide either 'oldText' (exact, unique match) or 'startLine'/'endLine' to target a section.");
        }

        Path backupPath;
        try {
            backupPath = backupRestoreService.backup(context.repositoryRoot(), context.session().getId(), file);
        } catch (IOException e) {
            return ToolExecutionResult.failure("Failed to create a backup before modifying: " + e.getMessage());
        }

        try {
            Files.writeString(file, newContent);
        } catch (IOException e) {
            return ToolExecutionResult.failure(
                    "Failed to write replacement (original file is unchanged; backup preserved at "
                            + backupPath + "): " + e.getMessage());
        }

        String diff = DiffUtil.unifiedDiff(oldContent, newContent, relativePath);
        String summary = "Replaced a section of " + relativePath + " (backup saved).";
        return ToolExecutionResult.okWithDiff(summary, Map.of(
                "path", relativePath,
                "changeType", "MODIFIED"
        ), diff, backupPath.toString());
    }

    private int countOccurrences(String haystack, String needle) {
        if (needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private int intParam(Map<String, Object> params, String key) {
        Object v = params.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(v));
    }
}
