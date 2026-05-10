package com.agentdev.core.model;

import java.io.Serializable;

public record JiraIssue(
    String key,
    String summary,
    String description,
    String acceptanceCriteria,
    TicketType type,
    TicketStatus status
) implements Serializable {}
