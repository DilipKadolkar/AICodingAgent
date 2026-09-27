package com.codecafe.aicodingagent.exception;

public class CommandNotApprovedException extends RuntimeException {
    public CommandNotApprovedException(String command) {
        super("Command is not on the approved list and was not executed: " + command);
    }
}
