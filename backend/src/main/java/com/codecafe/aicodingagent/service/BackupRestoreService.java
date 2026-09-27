package com.codecafe.aicodingagent.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * Creates and restores backups of files before the agent modifies them, so
 * every change is reversible. Backups live under
 * {@code <repo-root>/.ai-coding-agent-backups/<session-id>/}.
 */
@Service
public class BackupRestoreService {

    private static final String BACKUP_DIR_NAME = ".ai-coding-agent-backups";

    /** Copies the current contents of {@code targetFile} to a timestamped backup path. */
    public Path backup(Path repositoryRoot, String sessionId, Path targetFile) throws IOException {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        Path relative = root.relativize(targetFile.toAbsolutePath().normalize());
        String flatName = System.currentTimeMillis() + "__" + relative.toString().replace('/', '_').replace('\\', '_');

        Path backupDir = root.resolve(BACKUP_DIR_NAME).resolve(sessionId);
        Files.createDirectories(backupDir);
        Path backupFile = backupDir.resolve(flatName);
        Files.copy(targetFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
        return backupFile;
    }

    /** Restores {@code targetFile} from a previously created backup, overwriting its current content. */
    public void restore(Path backupFile, Path targetFile) throws IOException {
        if (!Files.exists(backupFile)) {
            throw new IOException("Backup file not found: " + backupFile);
        }
        Files.copy(backupFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
    }

    public static Instant now() {
        return Instant.now();
    }
}
