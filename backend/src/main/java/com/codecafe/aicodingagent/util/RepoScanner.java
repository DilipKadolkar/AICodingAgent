package com.codecafe.aicodingagent.util;

import java.nio.file.Path;
import java.util.Set;

/** Shared helpers for tools that walk a repository tree. */
public final class RepoScanner {

    public static final Set<String> EXCLUDED_DIR_NAMES = Set.of(
            ".git", "node_modules", "target", "build", "dist", ".idea", ".vscode",
            ".ai-coding-agent-backups", "__pycache__", ".venv", "venv", ".gradle");

    public static final long MAX_READABLE_FILE_BYTES = 2_000_000L;

    private RepoScanner() {
    }

    public static boolean containsExcludedSegment(Path path) {
        for (Path segment : path) {
            if (EXCLUDED_DIR_NAMES.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    public static boolean looksLikeTextFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        for (String binaryExt : new String[]{".png", ".jpg", ".jpeg", ".gif", ".bmp", ".ico",
                ".pdf", ".zip", ".jar", ".war", ".class", ".so", ".dll", ".exe", ".woff", ".woff2",
                ".ttf", ".mp3", ".mp4", ".mov"}) {
            if (name.endsWith(binaryExt)) {
                return false;
            }
        }
        return true;
    }
}
