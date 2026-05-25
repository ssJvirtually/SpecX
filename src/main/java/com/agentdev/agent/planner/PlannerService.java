package com.agentdev.agent.planner;

import org.springframework.stereotype.Service;

import com.agentdev.client.confluence.ConfluenceClient;
import com.agentdev.client.jira.JiraClient;
import com.agentdev.client.jira.dto.JiraIssueRequest;
import com.agentdev.core.model.JiraTicket;
import com.agentdev.core.model.TicketPlan;
import com.agentdev.core.model.JiraIssue;
import com.agentdev.core.model.TicketType;
import com.agentdev.core.model.TicketStatus;

import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PlannerService {

    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);

    private final ConfluenceClient confluenceClient;
    private final JiraClient       jiraClient;
    private final PlannerAiAgent   plannerAiAgent;

    public PlannerService(ConfluenceClient confluenceClient, JiraClient jiraClient, PlannerAiAgent plannerAiAgent) {
        this.confluenceClient = confluenceClient;
        this.jiraClient = jiraClient;
        this.plannerAiAgent = plannerAiAgent;
    }

    public static record CreatedTickets(
        List<JiraIssue> dbTickets,
        List<JiraIssue> beTickets,
        List<JiraIssue> feTickets
    ) {}

    public CreatedTickets processPage(String pageId) {
        String content  = confluenceClient.getPageContent(pageId);
        TicketPlan plan = plannerAiAgent.analyzeRequirements(content);

        List<JiraIssue> dbTickets = new ArrayList<>();
        List<JiraIssue> beTickets = new ArrayList<>();
        List<JiraIssue> feTickets = new ArrayList<>();

        if (plan.databaseTickets() != null) {
            for (JiraTicket t : plan.databaseTickets()) {
                dbTickets.add(createJiraTicket(t, "agent-db", TicketType.DATABASE));
            }
        }
        if (plan.backendTickets() != null) {
            for (JiraTicket t : plan.backendTickets()) {
                beTickets.add(createJiraTicket(t, "agent-be", TicketType.BACKEND));
            }
        }
        if (plan.frontendTickets() != null) {
            for (JiraTicket t : plan.frontendTickets()) {
                feTickets.add(createJiraTicket(t, "agent-fe", TicketType.FRONTEND));
            }
        }

        log.info("Created {} FE / {} BE / {} DB tickets from page {}",
            feTickets.size(), beTickets.size(), dbTickets.size(), pageId);

        return new CreatedTickets(dbTickets, beTickets, feTickets);
    }

    private JiraIssue createJiraTicket(JiraTicket ticket, String label, TicketType type) {
        String description = ticket.description() + "\n\n*Acceptance Criteria:*\n" + ticket.acceptanceCriteria();
        JiraIssueRequest req = JiraIssueRequest.builder()
            .summary(ticket.title())
            .description(description)
            .labels(List.of(label, "agent-generated"))
            .storyPoints(ticket.storyPoints())
            .build();
        String key = jiraClient.createIssue(req);
        log.info("Created {} — {}", key, ticket.title());
        return new JiraIssue(key, ticket.title(), description, ticket.acceptanceCriteria(), type, TicketStatus.TODO);
    }
}
