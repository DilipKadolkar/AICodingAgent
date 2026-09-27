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

class ModifyFileToolTest {

    @TempDir
    Path repoRoot;

    private final BackupRestoreService backupRestoreService = new BackupRestoreService();
    private final ModifyFileTool modifyFileTool = new ModifyFileTool(backupRestoreService);
    private final CreateFileTool createFileTool = new CreateFileTool();

    private ToolContext context(CodingSession session) {
        return new ToolContext(session, repoRoot);
    }

    @Test
    void modifyingFileCreatesByteIdenticalBackupAndAllowsRestore() throws IOException {
        CodingSession session = new CodingSession("t", repoRoot.toString());
        ToolContext ctx = context(session);
        Files.writeString(repoRoot.resolve("a.txt"), "original content");

        ToolExecutionResult result = modifyFileTool.execute(ctx, Map.of("path", "a.txt", "content", "new content"));
        assertThat(result.success()).isTrue();
        assertThat(Files.readString(repoRoot.resolve("a.txt"))).isEqualTo("new content");

        Path backupPath = Path.of(result.backupPath());
        assertThat(Files.readString(backupPath)).isEqualTo("original content");

        backupRestoreService.restore(backupPath, repoRoot.resolve("a.txt"));
        assertThat(Files.readString(repoRoot.resolve("a.txt"))).isEqualTo("original content");
    }

    @Test
    void producesADiffShowingAddedAndRemovedLines() throws IOException {
        CodingSession session = new CodingSession("t", repoRoot.toString());
        ToolContext ctx = context(session);
        Files.writeString(repoRoot.resolve("a.txt"), "one\ntwo\nthree");

        ToolExecutionResult result = modifyFileTool.execute(ctx, Map.of("path", "a.txt", "content", "one\nTWO\nthree"));
        assertThat(result.diff()).contains("-two").contains("+TWO");
    }

    @Test
    void refusesToModifyNonExistentFile() {
        CodingSession session = new CodingSession("t", repoRoot.toString());
        ToolExecutionResult result = modifyFileTool.execute(context(session), Map.of("path", "missing.txt", "content", "x"));
        assertThat(result.success()).isFalse();
    }

    @Test
    void createFileToolRefusesToOverwriteExistingFile() throws IOException {
        CodingSession session = new CodingSession("t", repoRoot.toString());
        Files.writeString(repoRoot.resolve("exists.txt"), "already here");
        ToolExecutionResult result = createFileTool.execute(context(session), Map.of("path", "exists.txt", "content", "new"));
        assertThat(result.success()).isFalse();
        assertThat(Files.readString(repoRoot.resolve("exists.txt"))).isEqualTo("already here");
    }

    @Test
    void createFileToolCreatesIntermediateDirectories() {
        CodingSession session = new CodingSession("t", repoRoot.toString());
        ToolExecutionResult result = createFileTool.execute(context(session),
                Map.of("path", "a/b/c/new.txt", "content", "hi"));
        assertThat(result.success()).isTrue();
        assertThat(repoRoot.resolve("a/b/c/new.txt")).exists();
    }

    @Test
    void sequentialModificationsProduceDistinctBackups() throws IOException, InterruptedException {
        CodingSession session = new CodingSession("t", repoRoot.toString());
        ToolContext ctx = context(session);
        Files.writeString(repoRoot.resolve("a.txt"), "v1");

        ToolExecutionResult r1 = modifyFileTool.execute(ctx, Map.of("path", "a.txt", "content", "v2"));
        Thread.sleep(5);
        ToolExecutionResult r2 = modifyFileTool.execute(ctx, Map.of("path", "a.txt", "content", "v3"));

        assertThat(r1.backupPath()).isNotEqualTo(r2.backupPath());
        assertThat(Files.readString(Path.of(r1.backupPath()))).isEqualTo("v1");
        assertThat(Files.readString(Path.of(r2.backupPath()))).isEqualTo("v2");
    }
}
