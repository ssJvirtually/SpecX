package com.agentdev.core.model;

import java.io.Serializable;
import java.util.List;

public record TicketPlan(
    String confluencePageId,
    String requirementSummary,
    List<JiraTicket> frontendTickets,
    List<JiraTicket> backendTickets,
    List<JiraTicket> databaseTickets
) implements Serializable {}
