package com.agentdev.core.exception;

public class ClaudeCodeFailedException extends AgentException {
    public ClaudeCodeFailedException(String message) {
        super(message);
    }
    public ClaudeCodeFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
