package com.codecafe.aicodingagent.util;

import com.codecafe.aicodingagent.exception.UnsafePathException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves a user/agent-supplied relative path against a repository root and
 * guarantees the result cannot escape that root - via "..", an absolute
 * path, or a symlink pointing outside it.
 */
public final class PathSafety {

    private PathSafety() {
    }

    public static Path resolveWithinRoot(Path repositoryRoot, String requestedRelativePath) {
        if (requestedRelativePath == null) {
            requestedRelativePath = "";
        }
        Path root = repositoryRoot.toAbsolutePath().normalize();
        Path candidate = root.resolve(requestedRelativePath).normalize();

        if (!candidate.startsWith(root)) {
            throw new UnsafePathException(requestedRelativePath);
        }

        // Resolve symlinks for anything that already exists, and re-check:
        // a symlink inside the repo could still point outside it.
        if (Files.exists(candidate)) {
            try {
                Path real = candidate.toRealPath();
                Path realRoot = root.toRealPath();
                if (!real.startsWith(realRoot)) {
                    throw new UnsafePathException(requestedRelativePath);
                }
            } catch (IOException e) {
                throw new UnsafePathException(requestedRelativePath);
            }
        }
        return candidate;
    }
}
