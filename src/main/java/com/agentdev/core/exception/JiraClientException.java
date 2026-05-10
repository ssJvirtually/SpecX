package com.agentdev.core.exception;

public class JiraClientException extends AgentException {
    public JiraClientException(String message) {
        super(message);
    }
    public JiraClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
