package com.agentdev.core.exception;

public class ClaudeCodeTimeoutException extends AgentException {
    public ClaudeCodeTimeoutException(String message) {
        super(message);
    }
    public ClaudeCodeTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
