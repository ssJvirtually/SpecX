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
        log.info("Starting fully sequential linear execution of agent tickets...");

        // 1. Database agents first
        log.info("Processing Database tickets (Count: {})...", dbTickets.size());
        for (JiraIssue ticket : dbTickets) {
            try {
                log.info(">>>> DB Agent starting: {}", ticket.key());
                dbAgentService.processTicket(ticket);
                log.info("<<<< DB Agent completed: {}", ticket.key());
            } catch (Exception e) {
                log.error("Database ticket {} failed (continuing): {}", ticket.key(), e.getMessage(), e);
            }
        }

        // 2. Backend agents second
        log.info("Processing Backend tickets (Count: {})...", beTickets.size());
        for (JiraIssue ticket : beTickets) {
            try {
                log.info(">>>> BE Agent starting: {}", ticket.key());
                backendAgentService.processTicket(ticket);
                log.info("<<<< BE Agent completed: {}", ticket.key());
            } catch (Exception e) {
                log.error("Backend ticket {} failed (continuing): {}", ticket.key(), e.getMessage(), e);
            }
        }

        // 3. Frontend agents third
        log.info("Processing Frontend tickets (Count: {})...", feTickets.size());
        for (JiraIssue ticket : feTickets) {
            try {
                log.info(">>>> FE Agent starting: {}", ticket.key());
                frontendAgentService.processTicket(ticket);
                log.info("<<<< FE Agent completed: {}", ticket.key());
            } catch (Exception e) {
                log.error("Frontend ticket {} failed (continuing): {}", ticket.key(), e.getMessage(), e);
            }
        }
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
