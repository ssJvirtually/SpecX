package com.agentdev.agent.frontend;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.agentdev.client.jira.JiraClient;
import com.agentdev.core.model.JiraIssue;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
@Component
public class FrontendTicketPoller {

    private final JiraClient           jiraClient;
    private final FrontendAgentService frontendAgentService;

    public FrontendTicketPoller(JiraClient jiraClient, FrontendAgentService frontendAgentService) {
        this.jiraClient = jiraClient;
        this.frontendAgentService = frontendAgentService;
    }

    private final ExecutorService pool = Executors.newFixedThreadPool(3);

    @Scheduled(fixedDelayString = "${agent.frontend.poll-interval-ms:300000}")
    public void poll() {
        List<JiraIssue> tickets = jiraClient.searchIssues(
            "labels = 'agent-fe' AND status = 'To Do' ORDER BY created ASC");

        tickets.stream()
            .limit(3)
            .forEach(t -> pool.submit(() -> frontendAgentService.processTicket(t)));
    }
}
