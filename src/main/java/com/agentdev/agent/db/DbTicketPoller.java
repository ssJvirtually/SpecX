package com.agentdev.agent.db;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.agentdev.client.jira.JiraClient;
import com.agentdev.core.model.JiraIssue;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
@Component
public class DbTicketPoller {

    private final JiraClient     jiraClient;
    private final DbAgentService dbAgentService;

    public DbTicketPoller(JiraClient jiraClient, DbAgentService dbAgentService) {
        this.jiraClient = jiraClient;
        this.dbAgentService = dbAgentService;
    }

    private final ExecutorService pool = Executors.newFixedThreadPool(3);

    @Scheduled(fixedDelayString = "${agent.database.poll-interval-ms:300000}")
    public void poll() {
        List<JiraIssue> tickets = jiraClient.searchIssues(
            "labels = 'agent-db' AND status = 'To Do' ORDER BY created ASC");

        tickets.stream()
            .limit(3)
            .forEach(t -> pool.submit(() -> dbAgentService.processTicket(t)));
    }
}
