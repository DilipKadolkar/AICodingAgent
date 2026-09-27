package com.codecafe.aicodingagent.tool;

import com.codecafe.aicodingagent.domain.CodingSession;
import com.codecafe.aicodingagent.exception.UnsafePathException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListFilesToolTest {

    @TempDir
    Path repoRoot;

    private final ListFilesTool tool = new ListFilesTool();
    private ToolContext context;

    @BeforeEach
    void setUp() throws IOException {
        context = new ToolContext(new CodingSession("t", repoRoot.toString()), repoRoot);
        Files.writeString(repoRoot.resolve("README.md"), "hello");
        Files.createDirectories(repoRoot.resolve("src"));
        Files.writeString(repoRoot.resolve("src/Main.java"), "class Main {}");
        Files.createDirectories(repoRoot.resolve("node_modules/some-pkg"));
        Files.writeString(repoRoot.resolve("node_modules/some-pkg/index.js"), "module.exports = {}");
    }

    @Test
    void listsFilesAndDirectoriesExcludingNoiseDirs() {
        ToolExecutionResult result = tool.execute(context, Map.of("path", ""));
        assertThat(result.success()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.data().get("entries");
        List<String> paths = entries.stream().map(e -> String.valueOf(e.get("path"))).toList();

        assertThat(paths).contains("README.md", "src", "src/Main.java");
        assertThat(paths).noneMatch(p -> p.contains("node_modules"));
    }

    @Test
    void returnsEmptyResultForEmptyDirectory() throws IOException {
        Path emptyDir = repoRoot.resolve("empty");
        Files.createDirectories(emptyDir);
        ToolExecutionResult result = tool.execute(context, Map.of("path", "empty"));
        assertThat(result.success()).isTrue();
        assertThat((List<?>) result.data().get("entries")).isEmpty();
    }

    @Test
    void rejectsPathTraversalAttempt() {
        assertThatThrownBy(() -> tool.execute(context, Map.of("path", "../../etc")))
                .isInstanceOf(UnsafePathException.class);
    }

    @Test
    void failsGracefullyForNonExistentPath() {
        ToolExecutionResult result = tool.execute(context, Map.of("path", "does-not-exist"));
        assertThat(result.success()).isFalse();
    }
}
