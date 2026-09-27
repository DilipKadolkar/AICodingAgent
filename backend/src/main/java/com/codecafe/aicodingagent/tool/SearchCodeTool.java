package com.codecafe.aicodingagent.tool;

import com.codecafe.aicodingagent.domain.ToolName;
import com.codecafe.aicodingagent.util.PathSafety;
import com.codecafe.aicodingagent.util.RepoScanner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
public class SearchCodeTool implements CodeAgentTool {

    private static final int DEFAULT_MAX_RESULTS = 100;
    private static final int CONTEXT_LINES = 1;

    @Override
    public ToolName name() {
        return ToolName.SEARCH_CODE;
    }

    @Override
    public ToolExecutionResult execute(ToolContext context, Map<String, Object> params) {
        Object queryObj = params.get("query");
        if (queryObj == null || String.valueOf(queryObj).isBlank()) {
            return ToolExecutionResult.failure("A non-empty 'query' parameter is required.");
        }
        String query = String.valueOf(queryObj);
        String relativePath = String.valueOf(params.getOrDefault("path", ""));
        boolean useRegex = Boolean.parseBoolean(String.valueOf(params.getOrDefault("regex", "false")));
        int maxResults = intParam(params, "maxResults", DEFAULT_MAX_RESULTS);

        Path start = PathSafety.resolveWithinRoot(context.repositoryRoot(), relativePath);
        if (!Files.exists(start)) {
            return ToolExecutionResult.failure("Path does not exist: " + relativePath);
        }

        Pattern pattern;
        try {
            pattern = useRegex ? Pattern.compile(query) : Pattern.compile(Pattern.quote(query));
        } catch (PatternSyntaxException e) {
            return ToolExecutionResult.failure("Invalid regex: " + e.getMessage());
        }

        List<Map<String, Object>> matches = new ArrayList<>();
        try (var stream = Files.walk(start)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();
            for (Path file : files) {
                if (matches.size() >= maxResults) {
                    break;
                }
                Path relativeToRoot = context.repositoryRoot().toAbsolutePath().normalize()
                        .relativize(file.toAbsolutePath().normalize());
                if (RepoScanner.containsExcludedSegment(relativeToRoot) || !RepoScanner.looksLikeTextFile(file)) {
                    continue;
                }
                if (Files.size(file) > RepoScanner.MAX_READABLE_FILE_BYTES) {
                    continue;
                }
                List<String> lines;
                try {
                    lines = Files.readAllLines(file);
                } catch (IOException e) {
                    continue; // not text-decodable, skip
                }
                for (int i = 0; i < lines.size() && matches.size() < maxResults; i++) {
                    if (pattern.matcher(lines.get(i)).find()) {
                        int from = Math.max(0, i - CONTEXT_LINES);
                        int to = Math.min(lines.size() - 1, i + CONTEXT_LINES);
                        matches.add(Map.of(
                                "path", relativeToRoot.toString().replace('\\', '/'),
                                "line", i + 1,
                                "matchedLine", lines.get(i),
                                "context", String.join("\n", lines.subList(from, to + 1))
                        ));
                    }
                }
            }
        } catch (IOException e) {
            return ToolExecutionResult.failure("Failed to search: " + e.getMessage());
        }

        String summary = "Found " + matches.size() + " match" + (matches.size() == 1 ? "" : "es")
                + " for \"" + query + "\"";
        return ToolExecutionResult.ok(summary, Map.of("matches", matches));
    }

    private int intParam(Map<String, Object> params, String key, int fallback) {
        Object v = params.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return fallback;
    }
}
