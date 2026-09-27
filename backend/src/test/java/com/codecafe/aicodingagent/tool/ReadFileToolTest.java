package com.codecafe.aicodingagent.tool;

import com.codecafe.aicodingagent.domain.CodingSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReadFileToolTest {

    @TempDir
    Path repoRoot;

    private final ReadFileTool tool = new ReadFileTool();

    private ToolContext context() {
        return new ToolContext(new CodingSession("t", repoRoot.toString()), repoRoot);
    }

    @Test
    void readsWholeFileWhenNoRangeGiven() throws IOException {
        Files.writeString(repoRoot.resolve("a.txt"), "line1\nline2\nline3");
        ToolExecutionResult result = tool.execute(context(), Map.of("path", "a.txt"));
        assertThat(result.success()).isTrue();
        assertThat(result.data().get("content")).isEqualTo("line1\nline2\nline3");
    }

    @Test
    void readsSpecificLineRange() throws IOException {
        Files.writeString(repoRoot.resolve("a.txt"), "line1\nline2\nline3\nline4");
        ToolExecutionResult result = tool.execute(context(), Map.of("path", "a.txt", "startLine", 2, "endLine", 3));
        assertThat(result.success()).isTrue();
        assertThat(result.data().get("content")).isEqualTo("line2\nline3");
    }

    @Test
    void failsForMissingFile() {
        ToolExecutionResult result = tool.execute(context(), Map.of("path", "missing.txt"));
        assertThat(result.success()).isFalse();
    }

    @Test
    void failsForDirectory() throws IOException {
        Files.createDirectories(repoRoot.resolve("dir"));
        ToolExecutionResult result = tool.execute(context(), Map.of("path", "dir"));
        assertThat(result.success()).isFalse();
    }
}
