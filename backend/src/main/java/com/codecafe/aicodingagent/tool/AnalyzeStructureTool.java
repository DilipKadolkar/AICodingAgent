package com.codecafe.aicodingagent.tool;

import com.codecafe.aicodingagent.domain.ToolName;
import com.codecafe.aicodingagent.util.RepoScanner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AnalyzeStructureTool implements CodeAgentTool {

    private static final int DEFAULT_MAX_DEPTH = 3;

    @Override
    public ToolName name() {
        return ToolName.ANALYZE_STRUCTURE;
    }

    @Override
    public ToolExecutionResult execute(ToolContext context, Map<String, Object> params) {
        Path root = context.repositoryRoot().toAbsolutePath().normalize();
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return ToolExecutionResult.failure("Repository root does not exist or is not a directory.");
        }

        String projectType = detectProjectType(root);
        List<String> entryPoints = detectEntryPoints(root, projectType);
        List<String> tree = buildTree(root, DEFAULT_MAX_DEPTH);

        String summary = "Detected project type: " + projectType + ". Found " + entryPoints.size()
                + " likely entry point(s).";
        return ToolExecutionResult.ok(summary, Map.of(
                "projectType", projectType,
                "entryPoints", entryPoints,
                "directoryTree", tree
        ));
    }

    private String detectProjectType(Path root) {
        if (Files.exists(root.resolve("pom.xml"))) {
            return "maven";
        }
        if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts"))) {
            return "gradle";
        }
        if (Files.exists(root.resolve("package.json"))) {
            return "npm";
        }
        if (Files.exists(root.resolve("requirements.txt")) || Files.exists(root.resolve("pyproject.toml"))) {
            return "python";
        }
        if (Files.exists(root.resolve("go.mod"))) {
            return "go";
        }
        return "unknown";
    }

    private List<String> detectEntryPoints(Path root, String projectType) {
        List<String> entryPoints = new ArrayList<>();
        try {
            switch (projectType) {
                case "maven", "gradle" -> {
                    try (var stream = Files.walk(root, 12)) {
                        stream.filter(Files::isRegularFile)
                                .filter(p -> p.toString().endsWith(".java"))
                                .filter(p -> !RepoScanner.containsExcludedSegment(root.relativize(p)))
                                .filter(this::containsMainMethod)
                                .limit(10)
                                .forEach(p -> entryPoints.add(root.relativize(p).toString().replace('\\', '/')));
                    }
                }
                case "npm" -> entryPoints.add("package.json (see \"scripts\" section)");
                case "python" -> {
                    if (Files.exists(root.resolve("main.py"))) {
                        entryPoints.add("main.py");
                    }
                    if (Files.exists(root.resolve("app.py"))) {
                        entryPoints.add("app.py");
                    }
                }
                case "go" -> entryPoints.add("go.mod (see module entry package)");
                default -> { /* no known entry points */ }
            }
        } catch (IOException ignored) {
            // best-effort detection only
        }
        return entryPoints;
    }

    private boolean containsMainMethod(Path javaFile) {
        try {
            String content = Files.readString(javaFile);
            return content.contains("public static void main(");
        } catch (IOException e) {
            return false;
        }
    }

    private List<String> buildTree(Path root, int maxDepth) {
        List<String> lines = new ArrayList<>();
        try (var stream = Files.walk(root, maxDepth)) {
            stream.sorted().forEach(p -> {
                Path relative = root.relativize(p);
                if (relative.toString().isEmpty() || RepoScanner.containsExcludedSegment(relative)) {
                    return;
                }
                int depth = relative.getNameCount() - 1;
                String indent = "  ".repeat(depth);
                String suffix = Files.isDirectory(p) ? "/" : "";
                lines.add(indent + relative.getFileName() + suffix);
            });
        } catch (IOException e) {
            lines.add("(failed to walk tree: " + e.getMessage() + ")");
        }
        return lines;
    }
}
