package com.agentdev.orchestrator;

import org.springframework.stereotype.Component;

import com.agentdev.agent.backend.BackendAgentService;
import com.agentdev.agent.db.DbAgentService;
import com.agentdev.agent.frontend.FrontendAgentService;
import com.agentdev.client.jira.JiraClient;
import com.agentdev.core.model.JiraIssue;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AgentCoordinator {

    private static final Logger log = LoggerFactory.getLogger(AgentCoordinator.class);

    private final JiraClient           jiraClient;
    private final DbAgentService       dbAgentService;
    private final BackendAgentService  backendAgentService;
    private final FrontendAgentService frontendAgentService;

    public AgentCoordinator(JiraClient jiraClient, DbAgentService dbAgentService, BackendAgentService backendAgentService, FrontendAgentService frontendAgentService) {
        this.jiraClient = jiraClient;
        this.dbAgentService = dbAgentService;
        this.backendAgentService = backendAgentService;
        this.frontendAgentService = frontendAgentService;
    }

    private final ExecutorService dbPool       = Executors.newSingleThreadExecutor();
    private final ExecutorService backendPool  = Executors.newFixedThreadPool(3);
    private final ExecutorService frontendPool = Executors.newFixedThreadPool(2);

    public void runBatch() {
        List<JiraIssue> dbTickets = jiraClient.searchIssues(
            "labels = 'agent-db' AND status = 'To Do'");
        List<JiraIssue> beTickets = jiraClient.searchIssues(
            "labels = 'agent-be' AND status = 'To Do'");
        List<JiraIssue> feTickets = jiraClient.searchIssues(
            "labels = 'agent-fe' AND status = 'To Do'");

        // DB first — block until all complete
        List<Future<?>> dbFutures = dbTickets.stream()
            .<Future<?>>map(t -> dbPool.submit(() -> dbAgentService.processTicket(t)))
            .toList();
        dbFutures.forEach(f -> {
            try { f.get(30, TimeUnit.MINUTES); }
            catch (Exception e) { log.warn("DB ticket failed, continuing: {}", e.getMessage()); }
        });

        // BE and FE in parallel
        Stream.concat(
            beTickets.stream().map(t -> backendPool.submit(()  -> backendAgentService.processTicket(t))),
            feTickets.stream().map(t -> frontendPool.submit(() -> frontendAgentService.processTicket(t)))
        ).toList().forEach(f -> {
            try { f.get(30, TimeUnit.MINUTES); }
            catch (Exception e) { log.error("Ticket failed: {}", e.getMessage()); }
        });
    }
}
