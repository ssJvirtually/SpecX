package com.agentdev.orchestrator;

import org.springframework.stereotype.Component;

import com.agentdev.agent.backend.BackendAgentService;
import com.agentdev.agent.db.DbAgentService;
import com.agentdev.agent.frontend.FrontendAgentService;
import com.agentdev.client.jira.JiraClient;
import com.agentdev.core.model.JiraIssue;
import com.agentdev.agent.planner.PlannerService;

import com.agentdev.core.repository.ConfluencePageRepository;
import com.agentdev.core.repository.JiraTicketRepository;
import com.agentdev.core.entity.ConfluencePageEntity;
import com.agentdev.core.entity.JiraTicketEntity;
import com.agentdev.client.confluence.ConfluenceClient;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
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

    private final JiraClient               jiraClient;
    private final DbAgentService           dbAgentService;
    private final BackendAgentService      backendAgentService;
    private final FrontendAgentService     frontendAgentService;
    private final PlannerService           plannerService;
    private final ConfluencePageRepository confluencePageRepository;
    private final JiraTicketRepository     jiraTicketRepository;

    public AgentCoordinator(
            JiraClient jiraClient,
            DbAgentService dbAgentService,
            BackendAgentService backendAgentService,
            FrontendAgentService frontendAgentService,
            PlannerService plannerService,
            ConfluencePageRepository confluencePageRepository,
            JiraTicketRepository jiraTicketRepository) {
        this.jiraClient = jiraClient;
        this.dbAgentService = dbAgentService;
        this.backendAgentService = backendAgentService;
        this.frontendAgentService = frontendAgentService;
        this.plannerService = plannerService;
        this.confluencePageRepository = confluencePageRepository;
        this.jiraTicketRepository = jiraTicketRepository;
    }

    private final ExecutorService dbPool       = Executors.newSingleThreadExecutor();
    private final ExecutorService backendPool  = Executors.newFixedThreadPool(3);
    private final ExecutorService frontendPool = Executors.newFixedThreadPool(2);

    public void orchestrate(String confluencePageId) {
        log.info("Starting linear orchestration for Confluence page: {}", confluencePageId);

        String pageId = ConfluenceClient.resolvePageId(confluencePageId);
        List<JiraIssue> dbTickets = new ArrayList<>();
        List<JiraIssue> beTickets = new ArrayList<>();
        List<JiraIssue> feTickets = new ArrayList<>();

        Optional<ConfluencePageEntity> existingPage = confluencePageRepository.findById(pageId);

        if (existingPage.isPresent()) {
            log.info("ℹ️ Confluence page {} was already processed. Retrieving existing tickets from PostgreSQL database...", pageId);
            List<JiraTicketEntity> savedTickets = jiraTicketRepository.findByConfluencePageId(pageId);

            for (JiraTicketEntity entity : savedTickets) {
                JiraIssue issue = new JiraIssue(
                    entity.getTicketKey(),
                    entity.getSummary(),
                    entity.getDescription(),
                    "",
                    entity.getTicketType(),
                    entity.getStatus()
                );

                if (entity.getTicketType() == com.agentdev.core.model.TicketType.DATABASE) {
                    dbTickets.add(issue);
                } else if (entity.getTicketType() == com.agentdev.core.model.TicketType.BACKEND) {
                    beTickets.add(issue);
                } else if (entity.getTicketType() == com.agentdev.core.model.TicketType.FRONTEND) {
                    feTickets.add(issue);
                }
            }
            log.info("ℹ️ Loaded {} FE / {} BE / {} DB tickets from database.", feTickets.size(), beTickets.size(), dbTickets.size());
        } else {
            log.info("ℹ️ Planning page for the first time: {}", pageId);
            
            PlannerService.CreatedTickets created = plannerService.processPage(confluencePageId);

            ConfluencePageEntity pageEntity = new ConfluencePageEntity(
                pageId,
                "Confluence Page " + pageId,
                confluencePageId,
                "PLANNED"
            );
            confluencePageRepository.save(pageEntity);

            for (JiraIssue t : created.dbTickets()) {
                dbTickets.add(t);
                jiraTicketRepository.save(new JiraTicketEntity(
                    pageId, t.key(), t.type(), t.status(), t.summary(), t.description()
                ));
            }
            for (JiraIssue t : created.beTickets()) {
                beTickets.add(t);
                jiraTicketRepository.save(new JiraTicketEntity(
                    pageId, t.key(), t.type(), t.status(), t.summary(), t.description()
                ));
            }
            for (JiraIssue t : created.feTickets()) {
                feTickets.add(t);
                jiraTicketRepository.save(new JiraTicketEntity(
                    pageId, t.key(), t.type(), t.status(), t.summary(), t.description()
                ));
            }
        }

        runTickets(dbTickets, beTickets, feTickets);

        log.info("Linear orchestration completed for Confluence page: {}", confluencePageId);
    }

    public void orchestrateAsync(String confluencePageId) {
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                orchestrate(confluencePageId);
            } catch (Exception e) {
                log.error("Orchestration failed for page: " + confluencePageId, e);
            }
        });
    }

    public void runTickets(List<JiraIssue> dbTickets, List<JiraIssue> beTickets, List<JiraIssue> feTickets) {
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

    public void runBatch() {
        List<JiraIssue> dbTickets = jiraClient.searchIssues(
            "labels = 'agent-db' AND status = 'To Do'");
        List<JiraIssue> beTickets = jiraClient.searchIssues(
            "labels = 'agent-be' AND status = 'To Do'");
        List<JiraIssue> feTickets = jiraClient.searchIssues(
            "labels = 'agent-fe' AND status = 'To Do'");

        runTickets(dbTickets, beTickets, feTickets);
    }
}
