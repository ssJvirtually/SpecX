package com.agentdev.agent.planner;

import org.springframework.stereotype.Service;

import com.agentdev.client.confluence.ConfluenceClient;
import com.agentdev.client.jira.JiraClient;
import com.agentdev.client.jira.dto.JiraIssueRequest;
import com.agentdev.core.model.JiraTicket;
import com.agentdev.core.model.TicketPlan;

import java.util.List;

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

    public void processPage(String pageId) {
        String content  = confluenceClient.getPageContent(pageId);
        TicketPlan plan = plannerAiAgent.analyzeRequirements(content);

        if (plan.frontendTickets() != null) plan.frontendTickets().forEach(t -> createJiraTicket(t, "agent-fe"));
        if (plan.backendTickets() != null) plan.backendTickets().forEach(t  -> createJiraTicket(t, "agent-be"));
        if (plan.databaseTickets() != null) plan.databaseTickets().forEach(t -> createJiraTicket(t, "agent-db"));

        log.info("Created {} FE / {} BE / {} DB tickets from page {}",
            plan.frontendTickets() != null ? plan.frontendTickets().size() : 0,
            plan.backendTickets() != null ? plan.backendTickets().size() : 0,
            plan.databaseTickets() != null ? plan.databaseTickets().size() : 0,
            pageId);
    }

    private void createJiraTicket(JiraTicket ticket, String label) {
        JiraIssueRequest req = JiraIssueRequest.builder()
            .summary(ticket.title())
            .description(ticket.description()
                + "\n\n*Acceptance Criteria:*\n" + ticket.acceptanceCriteria())
            .labels(List.of(label, "agent-generated"))
            .storyPoints(ticket.storyPoints())
            .build();
        String key = jiraClient.createIssue(req);
        log.info("Created {} — {}", key, ticket.title());
    }
}
