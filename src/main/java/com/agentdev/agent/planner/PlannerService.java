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

import com.agentdev.git.GitService;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.nio.file.Files;
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
    private final GitService       gitService;

    @Value("${github.backend-repo}")
    private String backendRepoUrl;

    public PlannerService(
            ConfluenceClient confluenceClient,
            JiraClient jiraClient,
            PlannerAiAgent plannerAiAgent,
            GitService gitService) {
        this.confluenceClient = confluenceClient;
        this.jiraClient = jiraClient;
        this.plannerAiAgent = plannerAiAgent;
        this.gitService = gitService;
    }

    public static record CreatedTickets(
        List<JiraIssue> dbTickets,
        List<JiraIssue> beTickets,
        List<JiraIssue> feTickets
    ) {}

    public CreatedTickets processPage(String pageId) {
        String content = confluenceClient.getPageContent(pageId);
        
        // 1. Temporary clone & codebase summary extraction
        String tempRepoPath = null;
        String codebaseSummary = "";
        try {
            log.info("Cloning repository {} for technical planning...", backendRepoUrl);
            tempRepoPath = gitService.cloneAndBranch(backendRepoUrl, "analysis-temp-" + System.currentTimeMillis());
            codebaseSummary = extractCodebaseSummary(tempRepoPath);
            log.info("Codebase analysis completed. Codebase Summary Length: {} chars", codebaseSummary.length());
        } catch (Exception e) {
            log.warn("Failed to clone or analyze codebase for planning context: {}. Proceeding with text-only requirements.", e.getMessage());
            codebaseSummary = "[Codebase summary unavailable]";
        } finally {
            if (tempRepoPath != null) {
                try {
                    gitService.cleanup(tempRepoPath);
                } catch (Exception e) {
                    log.warn("Failed to clean up temporary repository: {}", e.getMessage());
                }
            }
        }

        // 2. Build the codebase-aware prompt
        String combinedPrompt = "=== REQUIREMENTS ===\n" + content + "\n\n" +
                                "=== CODEBASE STRUCTURE ===\n" + codebaseSummary;

        TicketPlan plan = plannerAiAgent.analyzeRequirements(combinedPrompt);

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

    private String extractCodebaseSummary(String repoPath) {
        StringBuilder sb = new StringBuilder();
        File root = new File(repoPath);
        
        // 1. Scan for SQL migration files
        sb.append("--- DATABASE SCHEMA (EXISTING MIGRATIONS) ---\n");
        File migrationDir = new File(root, "src/main/resources/db/migration");
        if (migrationDir.exists() && migrationDir.isDirectory()) {
            File[] files = migrationDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".sql")) {
                        sb.append("File: ").append(file.getName()).append("\n");
                        try {
                            String sql = Files.readString(file.toPath());
                            sb.append(sql).append("\n\n");
                        } catch (Exception e) {
                            log.warn("Could not read migration file: " + file.getName());
                        }
                    }
                }
            }
        } else {
            sb.append("[No database migration files found]\n\n");
        }
        
        // 2. Scan file structure of Java classes
        sb.append("--- EXISTING FILES IN SRC/MAIN/JAVA ---\n");
        File javaDir = new File(root, "src/main/java");
        if (javaDir.exists() && javaDir.isDirectory()) {
            listJavaFilesRecursive(javaDir, repoPath, sb);
        } else {
            sb.append("[No Java src files found]\n");
        }
        
        return sb.toString();
    }

    private void listJavaFilesRecursive(File dir, String repoPath, StringBuilder sb) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                listJavaFilesRecursive(file, repoPath, sb);
            } else if (file.isFile() && file.getName().endsWith(".java")) {
                String relativePath = file.getAbsolutePath().substring(repoPath.length());
                // Standardize file paths for cross-platform matching
                sb.append(relativePath.replace("\\", "/")).append("\n");
            }
        }
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
