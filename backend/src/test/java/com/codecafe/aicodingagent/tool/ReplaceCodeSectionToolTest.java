package com.codecafe.aicodingagent.tool;

import com.codecafe.aicodingagent.domain.CodingSession;
import com.codecafe.aicodingagent.service.BackupRestoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReplaceCodeSectionToolTest {

    @TempDir
    Path repoRoot;

    private final ReplaceCodeSectionTool tool = new ReplaceCodeSectionTool(new BackupRestoreService());

    private ToolContext context() {
        return new ToolContext(new CodingSession("t", repoRoot.toString()), repoRoot);
    }

    @Test
    void replacesUniqueMatchByExactText() throws IOException {
        Files.writeString(repoRoot.resolve("a.java"), "int x = 1;\nint y = 2;\n");
        ToolExecutionResult result = tool.execute(context(),
                Map.of("path", "a.java", "oldText", "int x = 1;", "newText", "int x = 42;"));
        assertThat(result.success()).isTrue();
        assertThat(Files.readString(repoRoot.resolve("a.java"))).contains("int x = 42;");
    }

    @Test
    void failsAndLeavesFileUnchangedWhenNoMatch() throws IOException {
        String original = "int x = 1;\n";
        Files.writeString(repoRoot.resolve("a.java"), original);
        ToolExecutionResult result = tool.execute(context(),
                Map.of("path", "a.java", "oldText", "int z = 99;", "newText", "int z = 100;"));
        assertThat(result.success()).isFalse();
        assertThat(Files.readString(repoRoot.resolve("a.java"))).isEqualTo(original);
    }

    @Test
    void failsAndLeavesFileUnchangedWhenAmbiguousMatch() throws IOException {
        String original = "foo();\nfoo();\n";
        Files.writeString(repoRoot.resolve("a.java"), original);
        ToolExecutionResult result = tool.execute(context(),
                Map.of("path", "a.java", "oldText", "foo();", "newText", "bar();"));
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).containsIgnoringCase("ambiguous");
        assertThat(Files.readString(repoRoot.resolve("a.java"))).isEqualTo(original);
    }

    @Test
    void replacesByLineRange() throws IOException {
        Files.writeString(repoRoot.resolve("a.txt"), "one\ntwo\nthree\nfour");
        ToolExecutionResult result = tool.execute(context(),
                Map.of("path", "a.txt", "startLine", 2, "endLine", 3, "newText", "TWO_THREE"));
        assertThat(result.success()).isTrue();
        String content = Files.readString(repoRoot.resolve("a.txt"));
        assertThat(content).contains("one").contains("TWO_THREE").contains("four");
        assertThat(content).doesNotContain("two").doesNotContain("three");
    }

    @Test
    void rejectsInvalidLineRange() throws IOException {
        String original = "one\ntwo";
        Files.writeString(repoRoot.resolve("a.txt"), original);
        ToolExecutionResult result = tool.execute(context(),
                Map.of("path", "a.txt", "startLine", 5, "endLine", 6, "newText", "x"));
        assertThat(result.success()).isFalse();
        assertThat(Files.readString(repoRoot.resolve("a.txt"))).isEqualTo(original);
    }

    @Test
    void createsBackupBeforeReplacing() throws IOException {
        Files.writeString(repoRoot.resolve("a.txt"), "before");
        ToolExecutionResult result = tool.execute(context(),
                Map.of("path", "a.txt", "oldText", "before", "newText", "after"));
        assertThat(result.backupPath()).isNotNull();
        assertThat(Files.readString(Path.of(result.backupPath()))).isEqualTo("before");
    }
}
