package com.agentdev.core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Column;

import com.agentdev.core.model.TicketType;
import com.agentdev.core.model.TicketStatus;

@Entity
@Table(name = "jira_tickets")
public class JiraTicketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String confluencePageId;
    private String ticketKey;

    @Enumerated(EnumType.STRING)
    private TicketType ticketType;

    @Enumerated(EnumType.STRING)
    private TicketStatus status;

    private String summary;

    @Column(length = 4096)
    private String description;

    public JiraTicketEntity() {}

    public JiraTicketEntity(String confluencePageId, String ticketKey, TicketType ticketType, TicketStatus status, String summary, String description) {
        this.confluencePageId = confluencePageId;
        this.ticketKey = ticketKey;
        this.ticketType = ticketType;
        this.status = status;
        this.summary = summary;
        this.description = description;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getConfluencePageId() { return confluencePageId; }
    public void setConfluencePageId(String confluencePageId) { this.confluencePageId = confluencePageId; }

    public String getTicketKey() { return ticketKey; }
    public void setTicketKey(String ticketKey) { this.ticketKey = ticketKey; }

    public TicketType getTicketType() { return ticketType; }
    public void setTicketType(TicketType ticketType) { this.ticketType = ticketType; }

    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
