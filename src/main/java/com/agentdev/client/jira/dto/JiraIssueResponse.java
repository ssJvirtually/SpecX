package com.agentdev.client.jira.dto;

import java.io.Serializable;

public record JiraIssueResponse(
    String id,
    String key,
    String self
) implements Serializable {}
