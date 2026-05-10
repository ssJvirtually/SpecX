package com.agentdev.core.exception;

public class GitOperationException extends AgentException {
    public GitOperationException(String message) {
        super(message);
    }
    public GitOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
