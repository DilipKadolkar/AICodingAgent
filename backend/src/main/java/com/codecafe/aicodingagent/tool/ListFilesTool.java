package com.codecafe.aicodingagent.tool;

import com.codecafe.aicodingagent.domain.ToolName;
import com.codecafe.aicodingagent.util.PathSafety;
import com.codecafe.aicodingagent.util.RepoScanner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ListFilesTool implements CodeAgentTool {

    private static final int DEFAULT_MAX_DEPTH = 4;
    private static final int DEFAULT_MAX_ENTRIES = 300;

    @Override
    public ToolName name() {
        return ToolName.LIST_FILES;
    }

    @Override
    public ToolExecutionResult execute(ToolContext context, Map<String, Object> params) {
        String relativePath = String.valueOf(params.getOrDefault("path", ""));
        String glob = params.get("glob") == null ? null : String.valueOf(params.get("glob"));
        int maxDepth = intParam(params, "maxDepth", DEFAULT_MAX_DEPTH);
        int maxEntries = intParam(params, "maxEntries", DEFAULT_MAX_ENTRIES);

        Path start = PathSafety.resolveWithinRoot(context.repositoryRoot(), relativePath);
        if (!Files.exists(start)) {
            return ToolExecutionResult.failure("Path does not exist: " + relativePath);
        }

        PathMatcher matcher = glob == null ? null : FileSystems.getDefault().getPathMatcher("glob:" + glob);
        Path normalizedStart = start.toAbsolutePath().normalize();
        List<Map<String, Object>> entries = new ArrayList<>();
        try (var stream = Files.walk(start, Math.max(0, maxDepth))) {
            List<Path> paths = stream.toList();
            for (Path p : paths) {
                if (entries.size() >= maxEntries) {
                    break;
                }
                Path absolute = p.toAbsolutePath().normalize();
                if (absolute.equals(normalizedStart)) {
                    continue; // don't include the listing target itself, only its contents
                }
                Path relativeToRoot = context.repositoryRoot().toAbsolutePath().normalize().relativize(absolute);
                if (RepoScanner.containsExcludedSegment(relativeToRoot)) {
                    continue;
                }
                if (matcher != null && !matcher.matches(relativeToRoot)) {
                    continue;
                }
                entries.add(Map.of(
                        "path", relativeToRoot.toString().replace('\\', '/'),
                        "type", Files.isDirectory(p) ? "directory" : "file"
                ));
            }
        } catch (IOException | UncheckedIOException e) {
            return ToolExecutionResult.failure("Failed to list files: " + e.getMessage());
        }

        String summary = "Listed " + entries.size() + " entr" + (entries.size() == 1 ? "y" : "ies")
                + " under " + (relativePath.isBlank() ? "/" : relativePath);
        return ToolExecutionResult.ok(summary, Map.of("entries", entries));
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
