package com.codecafe.aicodingagent.exception;

/** Thrown whenever a tool-requested path resolves outside the session's repository root. */
public class UnsafePathException extends RuntimeException {
    public UnsafePathException(String path) {
        super("Path is not allowed because it resolves outside the repository root: " + path);
    }
}
