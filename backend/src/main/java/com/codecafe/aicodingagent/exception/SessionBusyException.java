package com.codecafe.aicodingagent.exception;

public class SessionBusyException extends RuntimeException {
    public SessionBusyException(String sessionId) {
        super("Session " + sessionId + " is already processing a request. "
                + "Wait for it to finish before submitting another message.");
    }
}
