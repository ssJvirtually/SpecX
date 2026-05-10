package com.agentdev.core.exception;

public class ConfluenceClientException extends AgentException {
    public ConfluenceClientException(String message) {
        super(message);
    }
    public ConfluenceClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
