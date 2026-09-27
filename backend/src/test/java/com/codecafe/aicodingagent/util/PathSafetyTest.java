package com.codecafe.aicodingagent.util;

import com.codecafe.aicodingagent.exception.UnsafePathException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PathSafetyTest {

    @TempDir
    Path repoRoot;

    @Test
    void resolvesOrdinaryRelativePathWithinRoot() {
        Path resolved = PathSafety.resolveWithinRoot(repoRoot, "src/Main.java");
        assertThat(resolved).isEqualTo(repoRoot.resolve("src/Main.java").normalize());
    }

    @Test
    void rejectsDotDotTraversal() {
        assertThatThrownBy(() -> PathSafety.resolveWithinRoot(repoRoot, "../../etc/passwd"))
                .isInstanceOf(UnsafePathException.class);
    }

    @Test
    void rejectsAbsolutePathOutsideRoot() {
        assertThatThrownBy(() -> PathSafety.resolveWithinRoot(repoRoot, "/etc/passwd"))
                .isInstanceOf(UnsafePathException.class);
    }

    @Test
    void rejectsSymlinkEscapingRoot() throws IOException {
        Path outside = Files.createTempDirectory("outside");
        Path secret = outside.resolve("secret.txt");
        Files.writeString(secret, "top secret");

        Path link = repoRoot.resolve("innocuous-link");
        try {
            Files.createSymbolicLink(link, secret);
        } catch (UnsupportedOperationException | IOException e) {
            // symlinks not supported in this environment; skip
            return;
        }

        assertThatThrownBy(() -> PathSafety.resolveWithinRoot(repoRoot, "innocuous-link"))
                .isInstanceOf(UnsafePathException.class);
    }

    @Test
    void allowsExistingFileInsideRoot() throws IOException {
        Path file = repoRoot.resolve("a.txt");
        Files.writeString(file, "hi");
        Path resolved = PathSafety.resolveWithinRoot(repoRoot, "a.txt");
        assertThat(Files.readString(resolved)).isEqualTo("hi");
    }
}
