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
import java.nio.charset.StandardCharsets;
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
    private final CodebaseSummarizerAgent codebaseSummarizerAgent;

    @Value("${github.backend-repo}")
    private String backendRepoUrl;

    public PlannerService(
            ConfluenceClient confluenceClient,
            JiraClient jiraClient,
            PlannerAiAgent plannerAiAgent,
            GitService gitService,
            CodebaseSummarizerAgent codebaseSummarizerAgent) {
        this.confluenceClient = confluenceClient;
        this.jiraClient = jiraClient;
        this.plannerAiAgent = plannerAiAgent;
        this.gitService = gitService;
        this.codebaseSummarizerAgent = codebaseSummarizerAgent;
    }

    public static record CreatedTickets(
        List<JiraIssue> dbTickets,
        List<JiraIssue> beTickets,
        List<JiraIssue> feTickets
    ) {}

    public CreatedTickets processPage(String pageId) {
        String content = confluenceClient.getPageContent(pageId);
        
        // 1. Clone & codebase summary extraction / generation
        String tempRepoPath = null;
        String codebaseSummary = "";
        try {
            log.info("Cloning repository {} default branch for technical planning...", backendRepoUrl);
            tempRepoPath = gitService.cloneDefaultBranch(backendRepoUrl);
            
            File summaryFile = new File(tempRepoPath, "CODEBASE_SUMMARY.md");
            if (summaryFile.exists()) {
                log.info("Found existing CODEBASE_SUMMARY.md in repository root.");
                codebaseSummary = Files.readString(summaryFile.toPath(), StandardCharsets.UTF_8);
            } else {
                log.info("CODEBASE_SUMMARY.md not found. Extracting raw metadata...");
                String rawMetadata = extractRawMetadata(tempRepoPath);
                log.info("Generating CODEBASE_SUMMARY.md using AI Summarizer Agent...");
                codebaseSummary = codebaseSummarizerAgent.summarize(rawMetadata);
                
                log.info("Saving and pushing new CODEBASE_SUMMARY.md to repository default branch...");
                Files.writeString(summaryFile.toPath(), codebaseSummary, StandardCharsets.UTF_8);
                gitService.commitAndPushDefault(tempRepoPath, "docs: add CODEBASE_SUMMARY.md [skip ci]");
                log.info("Successfully pushed CODEBASE_SUMMARY.md to default branch.");
            }
            log.info("Codebase analysis completed. Codebase Summary Length: {} chars", codebaseSummary.length());
        } catch (Exception e) {
            log.warn("Failed to clone, analyze codebase, or push summary for planning context: {}. Proceeding with text-only requirements.", e.getMessage(), e);
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

    private String extractRawMetadata(String repoPath) {
        StringBuilder sb = new StringBuilder();
        File root = new File(repoPath);

        // 1. Read Maven pom.xml if available (both root and banking-api)
        sb.append("--- BACKEND MAVEN POM.XML DETAILS ---\n");
        File rootPom = new File(root, "pom.xml");
        File apiPom = new File(root, "banking-api/pom.xml");
        if (apiPom.exists()) {
            sb.append("Location: banking-api/pom.xml\n");
            appendFileContent(apiPom, sb);
        } else if (rootPom.exists()) {
            sb.append("Location: pom.xml\n");
            appendFileContent(rootPom, sb);
        } else {
            sb.append("[No pom.xml found]\n");
        }
        sb.append("\n");

        // 2. Read Frontend package.json if available (both root and banking-web)
        sb.append("--- FRONTEND PACKAGE.JSON DETAILS ---\n");
        File rootPkg = new File(root, "package.json");
        File webPkg = new File(root, "banking-web/package.json");
        if (webPkg.exists()) {
            sb.append("Location: banking-web/package.json\n");
            appendFileContent(webPkg, sb);
        } else if (rootPkg.exists()) {
            sb.append("Location: package.json\n");
            appendFileContent(rootPkg, sb);
        } else {
            sb.append("[No package.json found]\n");
        }
        sb.append("\n");

        // 3. Scan for SQL migration files
        sb.append("--- DATABASE SCHEMA (EXISTING MIGRATIONS) ---\n");
        List<File> migrationDirs = List.of(
            new File(root, "banking-api/src/main/resources/db/migration"),
            new File(root, "src/main/resources/db/migration")
        );
        boolean foundMigrations = false;
        for (File migrationDir : migrationDirs) {
            if (migrationDir.exists() && migrationDir.isDirectory()) {
                File[] files = migrationDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && file.getName().endsWith(".sql")) {
                            foundMigrations = true;
                            sb.append("File: ").append(file.getName()).append("\n");
                            try {
                                String sql = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                                sb.append(sql).append("\n\n");
                            } catch (Exception e) {
                                log.warn("Could not read migration file: {}", file.getName());
                            }
                        }
                    }
                }
            }
        }
        if (!foundMigrations) {
            sb.append("[No database migration files found]\n\n");
        }

        // 4. Scan file structures recursively
        sb.append("--- KEY DIRECTORY STRUCTURES ---\n");
        // Scan Java source directories
        List<File> javaDirs = List.of(
            new File(root, "banking-api/src/main/java"),
            new File(root, "src/main/java")
        );
        boolean foundJava = false;
        for (File javaDir : javaDirs) {
            if (javaDir.exists() && javaDir.isDirectory()) {
                foundJava = true;
                sb.append("Java Source Directory (relative): ")
                  .append(javaDir.getAbsolutePath().substring(repoPath.length()).replace("\\", "/"))
                  .append("\n");
                listJavaFilesRecursive(javaDir, repoPath, sb);
            }
        }
        if (!foundJava) {
            sb.append("[No Java src files found]\n");
        }

        // Scan Frontend source directories
        List<File> webDirs = List.of(
            new File(root, "banking-web/src"),
            new File(root, "banking-web/app"),
            new File(root, "src")
        );
        boolean foundWeb = false;
        for (File webDir : webDirs) {
            if (webDir.exists() && webDir.isDirectory()) {
                if (webDir.getAbsolutePath().equals(new File(root, "src").getAbsolutePath()) && foundJava) {
                    continue; // Skip root/src fallback if we already scanned it as Java structure
                }
                foundWeb = true;
                sb.append("\nWeb Source Directory (relative): ")
                  .append(webDir.getAbsolutePath().substring(repoPath.length()).replace("\\", "/"))
                  .append("\n");
                listWebFilesRecursive(webDir, repoPath, sb);
            }
        }
        if (!foundWeb) {
            sb.append("[No Web src files found]\n");
        }

        return sb.toString();
    }

    private void appendFileContent(File file, StringBuilder sb) {
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            sb.append(content).append("\n");
        } catch (Exception e) {
            sb.append("[Error reading file: ").append(file.getName()).append("]\n");
        }
    }

    private void listWebFilesRecursive(File dir, String repoPath, StringBuilder sb) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                if (!file.getName().equals("node_modules")) {
                    listWebFilesRecursive(file, repoPath, sb);
                }
            } else if (file.isFile()) {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".ts") || name.endsWith(".tsx") || name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".css")) {
                    String relativePath = file.getAbsolutePath().substring(repoPath.length());
                    sb.append(relativePath.replace("\\", "/")).append("\n");
                }
            }
        }
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
