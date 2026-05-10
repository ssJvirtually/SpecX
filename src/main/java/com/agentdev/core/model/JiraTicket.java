package com.agentdev.core.model;

import java.io.Serializable;

public record JiraTicket(
    String title,
    String description,
    String acceptanceCriteria,
    TicketType type,
    int storyPoints
) implements Serializable {}
