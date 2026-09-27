package com.codecafe.aicodingagent.tool;

import com.codecafe.aicodingagent.domain.ToolName;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class IdentifyRelevantFilesTool implements CodeAgentTool {

    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "to", "of", "in", "on", "for", "with", "is", "are",
            "this", "that", "it", "add", "please", "make", "sure", "should", "when", "how",
            "does", "do", "can", "you", "we", "i", "code", "file", "files", "change", "update");

    private final SearchCodeTool searchCodeTool;
    private final AnalyzeStructureTool analyzeStructureTool;

    public IdentifyRelevantFilesTool(SearchCodeTool searchCodeTool, AnalyzeStructureTool analyzeStructureTool) {
        this.searchCodeTool = searchCodeTool;
        this.analyzeStructureTool = analyzeStructureTool;
    }

    @Override
    public ToolName name() {
        return ToolName.IDENTIFY_RELEVANT_FILES;
    }

    @Override
    public ToolExecutionResult execute(ToolContext context, Map<String, Object> params) {
        Object taskObj = params.get("taskDescription");
        if (taskObj == null || String.valueOf(taskObj).isBlank()) {
            return ToolExecutionResult.failure("A non-empty 'taskDescription' parameter is required.");
        }
        String taskDescription = String.valueOf(taskObj);

        List<String> keywords = extractKeywords(taskDescription);
        Map<String, Integer> hitCountByFile = new LinkedHashMap<>();
        Map<String, Set<String>> keywordsByFile = new LinkedHashMap<>();

        for (String keyword : keywords) {
            ToolExecutionResult result = searchCodeTool.execute(context, Map.of("query", keyword, "maxResults", 30));
            if (!result.success()) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> matches = (List<Map<String, Object>>) result.data().get("matches");
            for (Map<String, Object> match : matches) {
                String path = String.valueOf(match.get("path"));
                hitCountByFile.merge(path, 1, Integer::sum);
                keywordsByFile.computeIfAbsent(path, k -> new java.util.LinkedHashSet<>()).add(keyword);
            }
        }

        List<Map<String, Object>> ranked = hitCountByFile.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(10)
                .map(e -> Map.<String, Object>of(
                        "path", e.getKey(),
                        "occurrences", e.getValue(),
                        "matchedKeywords", keywordsByFile.get(e.getKey()),
                        "justification", "Matched keyword(s) " + String.join(", ", keywordsByFile.get(e.getKey()))
                                + " (" + e.getValue() + " occurrence(s))."
                ))
                .collect(Collectors.toList());

        if (ranked.isEmpty()) {
            ToolExecutionResult structure = analyzeStructureTool.execute(context, Map.of());
            if (structure.success()) {
                @SuppressWarnings("unchecked")
                List<String> entryPoints = (List<String>) structure.data().get("entryPoints");
                for (String ep : entryPoints) {
                    ranked.add(Map.of(
                            "path", ep,
                            "occurrences", 0,
                            "matchedKeywords", List.of(),
                            "justification", "No direct keyword match; suggested as a likely project entry point."
                    ));
                }
            }
        }

        String summary = "Identified " + ranked.size() + " potentially relevant file(s) for: \""
                + taskDescription + "\"";
        return ToolExecutionResult.ok(summary, Map.of("keywords", keywords, "relevantFiles", ranked));
    }

    private List<String> extractKeywords(String taskDescription) {
        List<String> keywords = new ArrayList<>();
        for (String raw : Arrays.stream(taskDescription.toLowerCase().split("[^a-zA-Z0-9_]+")).toList()) {
            if (raw.length() < 3 || STOPWORDS.contains(raw)) {
                continue;
            }
            if (!keywords.contains(raw)) {
                keywords.add(raw);
            }
            if (keywords.size() >= 8) {
                break;
            }
        }
        return keywords;
    }
}
