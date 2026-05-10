package com.agentdev.agent.backend;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.agentdev.client.jira.JiraClient;
import com.agentdev.core.model.JiraIssue;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
@Component
public class BackendTicketPoller {

    private final JiraClient          jiraClient;
    private final BackendAgentService backendAgentService;

    public BackendTicketPoller(JiraClient jiraClient, BackendAgentService backendAgentService) {
        this.jiraClient = jiraClient;
        this.backendAgentService = backendAgentService;
    }

    private final ExecutorService pool = Executors.newFixedThreadPool(3);

    @Scheduled(fixedDelayString = "${agent.backend.poll-interval-ms:300000}")
    public void poll() {
        List<JiraIssue> tickets = jiraClient.searchIssues(
            "labels = 'agent-be' AND status = 'To Do' ORDER BY created ASC");

        tickets.stream()
            .limit(3)
            .forEach(t -> pool.submit(() -> backendAgentService.processTicket(t)));
    }
}
