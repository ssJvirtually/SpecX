# Agentic Development Platform — Implementation Plan

> **How to use this document**
> Save as `IMPLEMENTATION_PLAN.md` at the root of the repository.
> When invoking Claude Code for any phase, start with:
> _"Read IMPLEMENTATION_PLAN.md before starting work."_

---

## Table of contents

1. [System overview](#1-system-overview)
2. [Tech stack](#2-tech-stack)
3. [Project structure](#3-project-structure)
4. [Single POM dependencies](#4-single-pom-dependencies)
5. [Phase 1 — Core models and exceptions](#phase-1--core-models-and-exceptions)
6. [Phase 2 — External API clients](#phase-2--external-api-clients)
7. [Phase 3 — Docker infrastructure](#phase-3--docker-infrastructure)
8. [Phase 4 — Git service](#phase-4--git-service)
9. [Phase 5 — Planner agent](#phase-5--planner-agent)
10. [Phase 6 — Specialized agents](#phase-6--specialized-agents)
11. [Phase 7 — Review agent](#phase-7--review-agent)
12. [Phase 8 — Documentation agent](#phase-8--documentation-agent)
13. [Phase 9 — Orchestrator and scheduler](#phase-9--orchestrator-and-scheduler)
14. [Configuration](#14-configuration)
15. [Docker setup](#15-docker-setup)
16. [Testing strategy](#16-testing-strategy)
17. [Implementation milestones](#17-implementation-milestones)

---

## 1. System overview

```
Confluence doc  (written by product / business person)
      │
      ▼
Planner Agent          ← reads Confluence, creates typed JIRA tickets
      │
      ▼
JIRA Board             ← tickets labeled: agent-fe / agent-be / agent-db
  │       │       │
  ▼       ▼       ▼
FE      BE      DB     ← pollers pick tickets, delegate to Claude Code
Agent  Agent  Agent
  │       │       │
  ▼       ▼       ▼
Claude Code (isolated Docker container per ticket)
  │       │       │
  ▼       ▼       ▼
Review Agent           ← validates output against acceptance criteria
      │
      ▼
Pull Requests (GitHub / GitLab)
      │
      ▼
Docs Agent             ← updates CLAUDE.md on every merged PR
```

### What each agent does

| Agent | Responsibility |
|---|---|
| Planner | Reads Confluence page, creates JIRA tickets (FE / BE / DB) |
| Backend Agent | Polls `agent-be` tickets, runs Claude Code, opens PR |
| Frontend Agent | Polls `agent-fe` tickets, runs Claude Code, opens PR |
| Database Agent | Polls `agent-db` tickets, runs Claude Code, validates SQL, opens PR |
| Review Agent | Validates Claude Code output against acceptance criteria |
| Docs Agent | Keeps `CLAUDE.md` updated after every merged PR |

---

## 2. Tech stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 3.3.x |
| Build | Maven | 3.9.x |
| AI / agent framework | LangChain4j | 0.36.2 |
| LLM | Anthropic Claude | claude-sonnet-4-20250514 |
| Claude Code CLI | @anthropic-ai/claude-code (in Docker) | latest |
| Git operations | JGit | 7.1.0 |
| GitHub API | kohsuke/github-api | 1.326 |
| HTTP client | Spring WebClient | 3.3.x |
| Containerisation | Docker via ProcessBuilder | — |
| Observability | Spring Actuator + Micrometer | 3.3.x |
| Testing | JUnit 5, Mockito, WireMock | — |

---

## 3. Project structure

One Spring Boot application. Packages separate concerns — no modules, no inter-module POMs.

```
agentic-dev-platform/
│
├── pom.xml                                      ← single POM, all dependencies here
├── IMPLEMENTATION_PLAN.md                       ← this file
├── CLAUDE.md                                    ← codebase map, auto-maintained by Docs Agent
├── .env                                         ← secrets, never committed
├── docker-compose.yml                           ← runs the app + prometheus
│
├── docker/
│   ├── Dockerfile                               ← Claude Code agent image (JDK + Node + Claude Code)
│   └── Dockerfile.app                           ← image for this Spring Boot app
│
└── src/
    ├── main/
    │   ├── java/com/agentdev/
    │   │   │
    │   │   ├── AgentDevApplication.java          ← @SpringBootApplication entry point
    │   │   │
    │   │   ├── core/                             ← shared models and exceptions only
    │   │   │   ├── model/
    │   │   │   │   ├── JiraIssue.java
    │   │   │   │   ├── JiraTicket.java
    │   │   │   │   ├── TicketPlan.java
    │   │   │   │   ├── TicketType.java
    │   │   │   │   ├── TicketStatus.java
    │   │   │   │   ├── ClaudeCodeResult.java
    │   │   │   │   ├── ReviewDecision.java
    │   │   │   │   ├── ReviewStatus.java
    │   │   │   │   └── PullRequestResult.java
    │   │   │   └── exception/
    │   │   │       ├── AgentException.java
    │   │   │       ├── ClaudeCodeTimeoutException.java
    │   │   │       ├── ClaudeCodeFailedException.java
    │   │   │       ├── JiraClientException.java
    │   │   │       ├── ConfluenceClientException.java
    │   │   │       └── GitOperationException.java
    │   │   │
    │   │   ├── client/                           ← external API wrappers
    │   │   │   ├── confluence/
    │   │   │   │   ├── ConfluenceClient.java
    │   │   │   │   ├── ConfluenceConfig.java
    │   │   │   │   └── dto/
    │   │   │   │       └── ConfluencePage.java
    │   │   │   ├── jira/
    │   │   │   │   ├── JiraClient.java
    │   │   │   │   ├── JiraConfig.java
    │   │   │   │   └── dto/
    │   │   │   │       ├── JiraIssueRequest.java
    │   │   │   │       └── JiraIssueResponse.java
    │   │   │   └── github/
    │   │   │       ├── GitHubClientService.java
    │   │   │       ├── GitHubConfig.java
    │   │   │       └── dto/
    │   │   │           └── PullRequestRequest.java
    │   │   │
    │   │   ├── git/                              ← JGit operations
    │   │   │   ├── GitService.java
    │   │   │   └── GitConfig.java
    │   │   │
    │   │   ├── docker/                           ← Claude Code container runner
    │   │   │   ├── DockerClaudeRunner.java
    │   │   │   ├── DockerConfig.java
    │   │   │   └── CacheWarmer.java
    │   │   │
    │   │   ├── agent/
    │   │   │   ├── base/
    │   │   │   │   └── BaseAgentService.java     ← shared poll → run → review → PR loop
    │   │   │   ├── planner/
    │   │   │   │   ├── PlannerAiAgent.java       ← LangChain4j @AiService
    │   │   │   │   └── PlannerService.java
    │   │   │   ├── backend/
    │   │   │   │   ├── BackendAgentService.java
    │   │   │   │   └── BackendTicketPoller.java
    │   │   │   ├── frontend/
    │   │   │   │   ├── FrontendAgentService.java
    │   │   │   │   └── FrontendTicketPoller.java
    │   │   │   ├── db/
    │   │   │   │   ├── DbAgentService.java
    │   │   │   │   ├── DbTicketPoller.java
    │   │   │   │   └── MigrationValidator.java
    │   │   │   ├── review/
    │   │   │   │   ├── ReviewAiAgent.java        ← LangChain4j @AiService
    │   │   │   │   └── ReviewService.java
    │   │   │   └── docs/
    │   │   │       ├── DocsAiAgent.java          ← LangChain4j @AiService
    │   │   │       ├── DocsAgentService.java
    │   │   │       └── GitHubWebhookController.java
    │   │   │
    │   │   └── orchestrator/
    │   │       ├── AgentCoordinator.java
    │   │       └── HealthController.java
    │   │
    │   └── resources/
    │       ├── application.yml
    │       └── prompts/
    │           ├── planner-system.txt
    │           ├── backend-system.txt
    │           ├── frontend-system.txt
    │           ├── db-system.txt
    │           ├── review-system.txt
    │           └── docs-system.txt
    │
    └── test/
        └── java/com/agentdev/
            ├── client/
            │   ├── ConfluenceClientTest.java
            │   ├── JiraClientTest.java
            │   └── GitHubClientServiceTest.java
            ├── agent/
            │   ├── planner/
            │   │   └── PlannerServiceTest.java
            │   ├── backend/
            │   │   └── BackendAgentServiceTest.java
            │   ├── review/
            │   │   └── ReviewServiceTest.java
            │   └── docs/
            │       └── DocsAgentServiceTest.java
            └── git/
                └── GitServiceTest.java
```

---

## 4. Single POM dependencies

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.2</version>
    </parent>

    <groupId>com.agentdev</groupId>
    <artifactId>agentic-dev-platform</artifactId>
    <version>1.0.0-SNAPSHOT</version>

    <properties>
        <java.version>21</java.version>
        <langchain4j.version>0.36.2</langchain4j.version>
        <jgit.version>7.1.0.202411261347-r</jgit.version>
        <github-api.version>1.326</github-api.version>
        <wiremock.version>3.9.1</wiremock.version>
    </properties>

    <dependencies>

        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.retry</groupId>
            <artifactId>spring-retry</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aspects</artifactId>
        </dependency>

        <!-- LangChain4j — Anthropic Claude -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-spring-boot-starter</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-anthropic-spring-boot-starter</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- Git -->
        <dependency>
            <groupId>org.eclipse.jgit</groupId>
            <artifactId>org.eclipse.jgit</artifactId>
            <version>${jgit.version}</version>
        </dependency>

        <!-- GitHub REST API -->
        <dependency>
            <groupId>org.kohsuke</groupId>
            <artifactId>github-api</artifactId>
            <version>${github-api.version}</version>
        </dependency>

        <!-- Flyway + H2 — used only by MigrationValidator -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Observability -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>

        <!-- Utilities -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>commons-io</groupId>
            <artifactId>commons-io</artifactId>
            <version>2.16.1</version>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.wiremock</groupId>
            <artifactId>wiremock-standalone</artifactId>
            <version>${wiremock.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>

    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>

</project>
```

---

## Phase 1 — Core models and exceptions

**Goal:** All shared data structures in place. No HTTP calls, no LLM, no Docker. Pure Java records and enums.

**Deliverables:** Everything under `com.agentdev.core`.

### Models to implement

```java
// TicketType.java
public enum TicketType { FRONTEND, BACKEND, DATABASE }

// TicketStatus.java
public enum TicketStatus { TODO, IN_PROGRESS, IN_REVIEW, DONE, FAILED }

// ReviewStatus.java
public enum ReviewStatus { APPROVED, NEEDS_REVISION }

// JiraTicket.java — used by Planner to represent a ticket to be created
public record JiraTicket(
    String title,
    String description,
    String acceptanceCriteria,
    TicketType type,
    int storyPoints
) {}

// JiraIssue.java — represents an existing ticket fetched from JIRA API
public record JiraIssue(
    String key,                  // e.g. "PROJ-42"
    String summary,
    String description,
    String acceptanceCriteria,
    TicketType type,
    TicketStatus status
) {}

// TicketPlan.java — Planner Agent output
public record TicketPlan(
    String confluencePageId,
    String requirementSummary,
    List<JiraTicket> frontendTickets,
    List<JiraTicket> backendTickets,
    List<JiraTicket> databaseTickets
) {}

// ClaudeCodeResult.java
public record ClaudeCodeResult(
    String rawOutput,
    boolean success,
    int exitCode,
    Duration duration
) {}

// ReviewDecision.java
public record ReviewDecision(
    ReviewStatus status,
    String summary,
    List<String> issues,       // specific problems found
    List<String> suggestions   // actionable fix instructions for Claude Code
) {}

// PullRequestResult.java
public record PullRequestResult(
    String url,
    int number,
    String branchName,
    String jiraKey
) {}
```

### Exception hierarchy

```java
// Base — all agent errors extend this
public class AgentException extends RuntimeException {
    public AgentException(String message) { super(message); }
    public AgentException(String message, Throwable cause) { super(message, cause); }
}

// Specific exceptions
public class ClaudeCodeTimeoutException  extends AgentException { ... }
public class ClaudeCodeFailedException   extends AgentException { ... }
public class JiraClientException         extends AgentException { ... }
public class ConfluenceClientException   extends AgentException { ... }
public class GitOperationException       extends AgentException { ... }
```

### Acceptance criteria

- [ ] All records and enums compile with Java 21
- [ ] All exceptions are serialisable
- [ ] Unit tests cover model construction edge cases (nulls, empty lists)

---

## Phase 2 — External API clients

**Goal:** Reliable, tested HTTP wrappers for Confluence, JIRA, and GitHub. No agent logic here — just clean API calls with proper error handling and retries.

**Deliverables:** Everything under `com.agentdev.client`.

### 2.1 Confluence client

```java
@Service
public class ConfluenceClient {

    // GET /wiki/api/v2/pages/{pageId}?body-format=storage
    // Strips Confluence XHTML storage format, returns plain text
    public String getPageContent(String pageId) { ... }
}
```

Auth: Basic auth — Base64 encode `username:api-token`.

### 2.2 JIRA client

```java
@Service
public class JiraClient {

    // POST /rest/api/3/issue — returns new issue key e.g. "PROJ-42"
    public String createIssue(JiraIssueRequest request) { ... }

    // GET /rest/api/3/search — JQL query, returns matching issues
    public List<JiraIssue> searchIssues(String jql) { ... }

    // POST /rest/api/3/issue/{key}/transitions
    public void transitionIssue(String issueKey, String transitionName) { ... }

    // POST /rest/api/3/issue/{key}/comment
    public void addComment(String issueKey, String body) { ... }
}
```

JQL queries used by each poller:
```
labels = "agent-be" AND status = "To Do" ORDER BY created ASC
labels = "agent-fe" AND status = "To Do" ORDER BY created ASC
labels = "agent-db" AND status = "To Do" ORDER BY created ASC
```

### 2.3 GitHub client service

```java
@Service
public class GitHubClientService {

    // Opens a PR, returns URL and PR number
    public PullRequestResult openPullRequest(String repoFullName, String title,
                                             String sourceBranch, String body) { ... }

    // Gets current file content from main branch (used by Docs Agent)
    public String getFileContent(String repoFullName, String filePath) { ... }

    // Commits updated file directly to main (used by Docs Agent)
    public void updateFile(String repoFullName, String filePath,
                           String content, String commitMessage) { ... }

    // Idempotency guard — skip if branch already exists
    public boolean branchExists(String repoFullName, String branchName) { ... }

    // Gets the full diff of a merged PR (used by Docs Agent)
    public String getPrDiff(String repoFullName, int prNumber) { ... }
}
```

### 2.4 WebClient configuration

```java
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient confluenceWebClient(
            @Value("${confluence.base-url}") String baseUrl,
            @Value("${confluence.username}") String username,
            @Value("${confluence.api-token}") String token) {
        String credentials = Base64.getEncoder()
            .encodeToString((username + ":" + token).getBytes());
        return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    // Same pattern for jiraWebClient
}
```

### 2.5 Retry on all client methods

```java
@Retryable(
    retryFor = { WebClientResponseException.class },
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)
)
public String getPageContent(String pageId) { ... }
```

Add `@EnableRetry` to `AgentDevApplication`.

### Acceptance criteria

- [ ] All three clients return real data against live APIs
- [ ] HTTP 4xx errors mapped to domain exceptions with clear messages
- [ ] HTTP 5xx errors trigger retry with exponential backoff
- [ ] Every public method has a WireMock test for success and error cases

---

## Phase 3 — Docker infrastructure

**Goal:** Claude Code runs in an isolated Docker container per ticket. Maven and npm caches persist across containers so downloads only happen once.

**Deliverables:** `docker/Dockerfile`, `DockerClaudeRunner`, `CacheWarmer`.

### 3.1 Agent Dockerfile (`docker/Dockerfile`)

```dockerfile
FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH="${JAVA_HOME}/bin:/opt/maven/bin:${PATH}"

# System tools
RUN apt-get update && apt-get install -y \
    curl git wget unzip ca-certificates gnupg python3 \
    && rm -rf /var/lib/apt/lists/*

# JDK 21
RUN apt-get update && apt-get install -y openjdk-21-jdk \
    && rm -rf /var/lib/apt/lists/*

# Maven 3.9
RUN wget -q https://downloads.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz \
    && tar -xzf apache-maven-3.9.6-bin.tar.gz -C /opt \
    && ln -s /opt/apache-maven-3.9.6 /opt/maven \
    && rm apache-maven-3.9.6-bin.tar.gz

# Node.js 20
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

# Claude Code CLI
RUN npm install -g @anthropic-ai/claude-code

# Verify all tools
RUN java -version && mvn -version && node -version && claude --version

WORKDIR /workspace
```

Build and volume setup (run once):
```bash
docker build -t claude-code-agent:latest -f docker/Dockerfile .
docker volume create agent-maven-cache
docker volume create agent-npm-cache
```

### 3.2 Docker runner

```java
@Component
public class DockerClaudeRunner {

    private static final String IMAGE        = "claude-code-agent:latest";
    private static final String MAVEN_VOLUME = "agent-maven-cache";
    private static final String NPM_VOLUME   = "agent-npm-cache";

    @Value("${anthropic.api-key}")       private String apiKey;
    @Value("${docker.timeout-minutes:20}") private int timeoutMinutes;
    @Value("${docker.memory-limit:4g}")  private String memoryLimit;
    @Value("${docker.cpu-limit:2.0}")    private String cpuLimit;

    public ClaudeCodeResult run(String repoPath, String prompt) {
        Instant start = Instant.now();

        List<String> command = List.of(
            "docker", "run", "--rm",
            "-v", repoPath + ":/workspace",
            "-v", MAVEN_VOLUME + ":/root/.m2",
            "-v", NPM_VOLUME + ":/root/.npm",
            "-w", "/workspace",
            "-e", "ANTHROPIC_API_KEY=" + apiKey,
            "--network", "bridge",
            "--memory", memoryLimit,
            "--cpus", cpuLimit,
            IMAGE,
            "claude", "--print",
            "--dangerously-skip-permissions",
            "--output-format", "json",
            prompt
        );

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Stream output line by line — never buffer to end of process
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.debug("[claude-code] {}", line);
                }
            }

            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new ClaudeCodeTimeoutException(
                    "Exceeded " + timeoutMinutes + " min timeout");
            }

            return new ClaudeCodeResult(
                output.toString(),
                process.exitValue() == 0,
                process.exitValue(),
                Duration.between(start, Instant.now())
            );

        } catch (IOException | InterruptedException e) {
            throw new ClaudeCodeFailedException("Container execution failed", e);
        }
    }
}
```

### 3.3 Cache warmer

```java
@Component
public class CacheWarmer {

    @Value("${docker.warmup.enabled:true}")
    private boolean warmupEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!warmupEnabled) return;
        log.info("Pre-warming dependency caches...");

        runAndWait(List.of(
            "docker", "run", "--rm",
            "-v", "agent-maven-cache:/root/.m2",
            "claude-code-agent:latest",
            "mvn", "dependency:get",
            "-Dartifact=org.springframework.boot:spring-boot-starter-web:3.3.2"
        ));

        runAndWait(List.of(
            "docker", "run", "--rm",
            "-v", "agent-npm-cache:/root/.npm",
            "claude-code-agent:latest",
            "npm", "install", "--prefer-offline",
            "react", "react-dom", "typescript", "axios"
        ));

        log.info("Cache warm-up complete");
    }

    private void runAndWait(List<String> command) {
        try {
            new ProcessBuilder(command).start().waitFor(10, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Warm-up step failed (non-fatal): {}", e.getMessage());
        }
    }
}
```

### Acceptance criteria

- [ ] Docker image builds successfully, all tools verified inside container
- [ ] `DockerClaudeRunner.run()` returns output for a simple prompt
- [ ] Container removed automatically after run (`--rm`)
- [ ] Timeout kills the container and throws `ClaudeCodeTimeoutException`
- [ ] Second run against the same Maven volume is noticeably faster

---

## Phase 4 — Git service

**Goal:** All JGit operations wrapped in one service. Agents never touch JGit directly.

**Deliverables:** `GitService.java`.

```java
@Service
public class GitService {

    @Value("${github.token}")
    private String gitToken;

    @Value("${git.workspace-dir:/tmp/agent-workspaces}")
    private String workspaceDir;

    // Clones repo and checks out a new branch. Returns local path.
    public String cloneAndBranch(String repoUrl, String branchName) {
        String localPath = workspaceDir + "/"
            + branchName.replace("/", "-") + "-" + System.currentTimeMillis();
        try {
            Files.createDirectories(Path.of(localPath));
            Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(new File(localPath))
                .setCredentialsProvider(credentials())
                .call();
            git.checkout().setCreateBranch(true).setName(branchName).call();
            git.close();
            return localPath;
        } catch (Exception e) {
            throw new GitOperationException("Clone failed for " + repoUrl, e);
        }
    }

    // Returns the unified diff of all uncommitted changes
    public String getDiff(String repoPath) {
        try (Git git = Git.open(new File(repoPath))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            git.diff().setOutputStream(out).call();
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new GitOperationException("Diff failed at " + repoPath, e);
        }
    }

    // Stages all changes, commits, and pushes the branch
    public void commitAndPush(String repoPath, String commitMessage, String branchName) {
        try (Git git = Git.open(new File(repoPath))) {
            git.add().addFilepattern(".").call();
            git.commit()
                .setMessage(commitMessage)
                .setAuthor("Agent Bot", "agent@agentdev.com")
                .call();
            git.push()
                .setCredentialsProvider(credentials())
                .setRefSpecs(new RefSpec(branchName + ":" + branchName))
                .call();
        } catch (Exception e) {
            throw new GitOperationException("Commit/push failed", e);
        }
    }

    // Always call in a finally block to free disk space
    public void cleanup(String repoPath) {
        FileUtils.deleteQuietly(new File(repoPath));
    }

    private CredentialsProvider credentials() {
        return new UsernamePasswordCredentialsProvider(gitToken, "");
    }
}
```

### Acceptance criteria

- [ ] `cloneAndBranch` creates a branch visible on the remote after push
- [ ] `getDiff` returns non-empty diff after a file is modified
- [ ] `cleanup` deletes the directory even when non-empty
- [ ] All methods throw `GitOperationException`, never raw JGit exceptions

---

## Phase 5 — Planner agent

**Goal:** Read a Confluence page, analyse requirements with Claude, create typed JIRA tickets automatically.

**Deliverables:** `PlannerAiAgent`, `PlannerService`, REST trigger endpoint.

### 5.1 AI service

```java
@AiService
public interface PlannerAiAgent {

    @SystemMessage(fromResource = "prompts/planner-system.txt")
    TicketPlan analyzeRequirements(@UserMessage String requirementsText);
}
```

### 5.2 System prompt (`resources/prompts/planner-system.txt`)

```
You are a senior technical project planner.

Given a product or business requirement document, decompose it into
engineering tasks and classify each as FRONTEND, BACKEND, or DATABASE.

Classification rules:
- FRONTEND  : React components, UI pages, forms, client-side logic, CSS
- BACKEND   : REST endpoints, business logic, Spring Boot services, integrations
- DATABASE  : New tables, column additions, index changes, Flyway migrations

For each ticket include:
- title             : short action-oriented title ("Add POST /payments endpoint")
- description       : what to build and why
- acceptanceCriteria: bullet list of verifiable done conditions
- type              : FRONTEND | BACKEND | DATABASE
- storyPoints       : 1, 2, 3, 5, or 8

One concern per ticket.
A database change and its backend usage are always separate tickets.
Return only valid JSON matching the TicketPlan schema. No explanation, no preamble.
```

### 5.3 Planner service

```java
@Service
@RequiredArgsConstructor
public class PlannerService {

    private final ConfluenceClient confluenceClient;
    private final JiraClient       jiraClient;
    private final PlannerAiAgent   plannerAiAgent;

    public void processPage(String pageId) {
        String content  = confluenceClient.getPageContent(pageId);
        TicketPlan plan = plannerAiAgent.analyzeRequirements(content);

        plan.frontendTickets().forEach(t -> createJiraTicket(t, "agent-fe"));
        plan.backendTickets().forEach(t  -> createJiraTicket(t, "agent-be"));
        plan.databaseTickets().forEach(t -> createJiraTicket(t, "agent-db"));

        log.info("Created {} FE / {} BE / {} DB tickets from page {}",
            plan.frontendTickets().size(),
            plan.backendTickets().size(),
            plan.databaseTickets().size(),
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
```

### 5.4 REST trigger

```java
@RestController
@RequestMapping("/api/planner")
@RequiredArgsConstructor
public class PlannerController {

    private final PlannerService plannerService;

    @PostMapping("/process")
    public ResponseEntity<String> process(@RequestParam String confluencePageId) {
        plannerService.processPage(confluencePageId);
        return ResponseEntity.accepted()
            .body("Processing started for page: " + confluencePageId);
    }
}
```

### Acceptance criteria

- [ ] Given a real Confluence page, creates correctly typed JIRA tickets
- [ ] Each ticket has a label (`agent-fe`, `agent-be`, or `agent-db`)
- [ ] LLM response JSON deserialises into `TicketPlan` without errors
- [ ] Tested with at least 3 different requirement documents

---

## Phase 6 — Specialized agents

**Goal:** Backend, Frontend, and Database agents that pick JIRA tickets, run Claude Code in Docker, and open PRs.

**Deliverables:** `BaseAgentService`, three agent services, three pollers.

### 6.1 Base agent — shared loop

All three agents extend this. Only the prompt and optional post-processing differ.

```java
public abstract class BaseAgentService {

    @Autowired protected JiraClient          jiraClient;
    @Autowired protected GitService          gitService;
    @Autowired protected DockerClaudeRunner  dockerRunner;
    @Autowired protected GitHubClientService gitHubClient;
    @Autowired protected ReviewService       reviewService;

    protected abstract String getJiraLabel();
    protected abstract String getTargetRepo();
    protected abstract String buildPrompt(JiraIssue ticket);

    // Override only in DbAgentService for migration validation
    protected void postProcess(String repoPath, JiraIssue ticket) {}

    public void processTicket(JiraIssue ticket) {
        String branch   = "agent/" + ticket.key().toLowerCase();
        String repoPath = null;

        try {
            // Idempotency guard
            if (gitHubClient.branchExists(getTargetRepo(), branch)) {
                log.info("Branch {} already exists, skipping", branch);
                return;
            }

            jiraClient.transitionIssue(ticket.key(), "In Progress");
            repoPath = gitService.cloneAndBranch(getTargetRepo(), branch);

            ClaudeCodeResult result = dockerRunner.run(repoPath, buildPrompt(ticket));
            if (!result.success()) { handleFailure(ticket, result); return; }

            postProcess(repoPath, ticket);

            ReviewDecision review = reviewService.review(ticket, gitService.getDiff(repoPath));

            // One revision attempt if needed
            if (review.status() == ReviewStatus.NEEDS_REVISION) {
                log.info("Revision requested for {} — retrying", ticket.key());
                dockerRunner.run(repoPath, buildRevisionPrompt(ticket, review));
                review = reviewService.review(ticket, gitService.getDiff(repoPath));
            }

            if (review.status() == ReviewStatus.APPROVED) {
                gitService.commitAndPush(repoPath,
                    ticket.key() + ": " + ticket.summary(), branch);
                PullRequestResult pr = gitHubClient.openPullRequest(
                    getTargetRepo(),
                    ticket.key() + ": " + ticket.summary(),
                    branch,
                    buildPrBody(ticket, review));
                jiraClient.transitionIssue(ticket.key(), "In Review");
                jiraClient.addComment(ticket.key(), "PR opened: " + pr.url());
            } else {
                handleFailure(ticket, result);
            }

        } finally {
            if (repoPath != null) gitService.cleanup(repoPath);
        }
    }

    private void handleFailure(JiraIssue ticket, ClaudeCodeResult result) {
        jiraClient.transitionIssue(ticket.key(), "Failed");
        jiraClient.addComment(ticket.key(),
            "Agent failed after retry. Manual implementation required.\n"
            + "Exit code: " + result.exitCode());
    }

    private String buildRevisionPrompt(JiraIssue ticket, ReviewDecision review) {
        return buildPrompt(ticket)
            + "\n\nYOUR PREVIOUS ATTEMPT WAS REJECTED. Fix these specific issues:\n"
            + String.join("\n", review.issues())
            + "\n\nSuggested fixes:\n"
            + String.join("\n", review.suggestions());
    }

    private String buildPrBody(JiraIssue ticket, ReviewDecision review) {
        return "Resolves " + ticket.key()
            + "\n\n" + ticket.description()
            + "\n\n**Review summary:** " + review.summary()
            + "\n\n_Generated by Agent Bot_";
    }
}
```

### 6.2 Backend agent

```java
@Service
public class BackendAgentService extends BaseAgentService {

    @Value("${github.backend-repo}") private String backendRepo;

    @Override protected String getJiraLabel()  { return "agent-be"; }
    @Override protected String getTargetRepo() { return backendRepo; }

    @Override
    protected String buildPrompt(JiraIssue ticket) {
        return """
            Read CLAUDE.md first to understand project structure and conventions.

            Implement this JIRA ticket in the Spring Boot backend codebase.

            Ticket  : %s
            Summary : %s

            Description:
            %s

            Acceptance Criteria:
            %s

            Rules:
            - Follow the package structure in CLAUDE.md exactly
            - Controllers delegate to services — no business logic in controllers
            - All service methods are @Transactional
            - Use Java records for DTOs
            - Write unit tests for every new public method (JUnit 5 + Mockito)
            - Run `mvn test` and fix all failures before finishing
            """.formatted(ticket.key(), ticket.summary(),
                          ticket.description(), ticket.acceptanceCriteria());
    }
}
```

### 6.3 Frontend agent

```java
@Service
public class FrontendAgentService extends BaseAgentService {

    @Value("${github.frontend-repo}") private String frontendRepo;

    @Override protected String getJiraLabel()  { return "agent-fe"; }
    @Override protected String getTargetRepo() { return frontendRepo; }

    @Override
    protected String buildPrompt(JiraIssue ticket) {
        return """
            Read CLAUDE.md first to understand the component structure and conventions.

            Implement this JIRA ticket in the React/TypeScript frontend codebase.

            Ticket  : %s
            Summary : %s

            Description:
            %s

            Acceptance Criteria:
            %s

            Rules:
            - TypeScript strict mode — no `any` types
            - Use existing design system components only
            - API calls go through React Query hooks in src/hooks/
            - Write component tests using the existing test setup
            - Run `npm test` and `npm run build` — fix all errors before finishing
            """.formatted(ticket.key(), ticket.summary(),
                          ticket.description(), ticket.acceptanceCriteria());
    }
}
```

### 6.4 Database agent

```java
@Service
public class DbAgentService extends BaseAgentService {

    @Value("${github.backend-repo}") private String backendRepo;
    @Autowired private MigrationValidator migrationValidator;

    @Override protected String getJiraLabel()  { return "agent-db"; }
    @Override protected String getTargetRepo() { return backendRepo; }

    @Override
    protected void postProcess(String repoPath, JiraIssue ticket) {
        String migrationsPath = repoPath + "/src/main/resources/db/migration";
        ValidationResult result = migrationValidator.validate(migrationsPath);
        if (!result.valid()) {
            throw new AgentException(
                "Migration validation failed for " + ticket.key() + ": " + result.error());
        }
    }

    @Override
    protected String buildPrompt(JiraIssue ticket) {
        return """
            Read CLAUDE.md first for database conventions.

            Create a Flyway SQL migration for this JIRA ticket.

            Ticket  : %s
            Summary : %s

            Description:
            %s

            Acceptance Criteria:
            %s

            Rules:
            - List existing files in src/main/resources/db/migration first
            - Name the file V{next_version}__{description}.sql
            - Never modify existing migration files
            - Add indexes for all new foreign key columns
            - Use TIMESTAMPTZ for timestamp columns
            - UUID primary keys with gen_random_uuid() default
            - Include rollback SQL in a comment block at the top
            """.formatted(ticket.key(), ticket.summary(),
                          ticket.description(), ticket.acceptanceCriteria());
    }
}
```

### 6.5 Migration validator

```java
@Component
public class MigrationValidator {

    public ValidationResult validate(String migrationsPath) {
        try {
            Flyway.configure()
                .dataSource("jdbc:h2:mem:validation_"
                    + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1", "sa", "")
                .locations("filesystem:" + migrationsPath)
                .load()
                .migrate();
            return ValidationResult.valid();
        } catch (FlywayException e) {
            return ValidationResult.invalid(e.getMessage());
        }
    }

    public record ValidationResult(boolean valid, String error) {
        static ValidationResult valid()             { return new ValidationResult(true, null); }
        static ValidationResult invalid(String err) { return new ValidationResult(false, err); }
    }
}
```

### 6.6 Ticket pollers

Each poller is identical except for the agent it calls. Backend shown — Frontend and DB follow the same pattern.

```java
@Component
@RequiredArgsConstructor
public class BackendTicketPoller {

    private final JiraClient          jiraClient;
    private final BackendAgentService backendAgentService;

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
```

Add `@EnableScheduling` to `AgentDevApplication`.

### Acceptance criteria

- [ ] Backend Agent picks a real `agent-be` ticket and opens a PR
- [ ] Frontend Agent picks a real `agent-fe` ticket and opens a PR
- [ ] Database Agent generates a valid migration — H2 validation passes
- [ ] Temp directories cleaned up on both success and failure
- [ ] Existing branch guard prevents duplicate PRs on restart
- [ ] JIRA transitions correctly: `To Do → In Progress → In Review`

---

## Phase 7 — Review agent

**Goal:** Validate Claude Code output against the ticket's acceptance criteria before any PR is opened.

**Deliverables:** `ReviewAiAgent`, `ReviewService`.

### 7.1 AI service

```java
@AiService
public interface ReviewAiAgent {

    @SystemMessage(fromResource = "prompts/review-system.txt")
    ReviewDecision review(@UserMessage String ticketAndDiff);
}
```

### 7.2 System prompt (`resources/prompts/review-system.txt`)

```
You are a senior engineer reviewing an AI-generated pull request.

You receive the JIRA ticket and the git diff.
Determine whether the implementation satisfies the requirements.

Check for:
- All acceptance criteria are met
- No files modified outside the ticket scope
- No obvious bugs or unhandled nulls
- Tests present for all new logic
- No hardcoded secrets or credentials
- Database migrations follow the naming convention (if applicable)

Return JSON only:
- status       : "APPROVED" or "NEEDS_REVISION"
- summary      : 1-2 sentence description of what was implemented
- issues       : list of specific problems found (empty if approved)
- suggestions  : actionable fix for each issue (empty if approved)

Focus on correctness and completeness only.
Do not suggest style or cosmetic improvements.
No preamble — return only valid JSON.
```

### 7.3 Review service

```java
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewAiAgent reviewAiAgent;
    private static final int MAX_DIFF_CHARS = 50_000;

    public ReviewDecision review(JiraIssue ticket, String diff) {
        if (diff.isBlank()) {
            return new ReviewDecision(ReviewStatus.NEEDS_REVISION,
                "No changes detected",
                List.of("Claude Code produced no file changes"),
                List.of("Re-implement the ticket. Ensure files are written to /workspace."));
        }

        String trimmedDiff = diff.length() > MAX_DIFF_CHARS
            ? diff.substring(0, MAX_DIFF_CHARS) + "\n... [truncated]"
            : diff;

        String prompt = """
            JIRA Ticket : %s
            Summary     : %s

            Description:
            %s

            Acceptance Criteria:
            %s

            Git Diff:
            %s
            """.formatted(ticket.key(), ticket.summary(),
                          ticket.description(), ticket.acceptanceCriteria(), trimmedDiff);

        return reviewAiAgent.review(prompt);
    }
}
```

### Acceptance criteria

- [ ] Returns `APPROVED` for a correct, complete implementation
- [ ] Returns `NEEDS_REVISION` with specific, actionable issues for an incomplete one
- [ ] Empty diff returns `NEEDS_REVISION` with clear feedback
- [ ] Handles diffs up to 50,000 characters without error

---

## Phase 8 — Documentation agent

**Goal:** Keep `CLAUDE.md` accurate automatically. Every merged PR triggers an update so Claude Code always starts with current codebase knowledge.

**Deliverables:** `DocsAiAgent`, `DocsAgentService`, `GitHubWebhookController`.

### 8.1 Webhook controller

```java
@RestController
@RequestMapping("/webhooks/github")
@RequiredArgsConstructor
public class GitHubWebhookController {

    private final DocsAgentService docsAgentService;
    private final WebhookVerifier  webhookVerifier;

    @PostMapping
    public ResponseEntity<Void> handle(
            @RequestHeader("X-GitHub-Event") String event,
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestBody String payload) {

        webhookVerifier.verify(payload, signature);   // HMAC-SHA256 verification

        if ("pull_request".equals(event)) {
            GitHubPrEvent pr = parse(payload, GitHubPrEvent.class);
            if ("closed".equals(pr.action()) && pr.pullRequest().merged()) {
                docsAgentService.updateDocs(pr);
            }
        }
        return ResponseEntity.ok().build();
    }
}
```

Register this URL in **GitHub repo → Settings → Webhooks**.
Subscribe to: `Pull requests` events only.

### 8.2 System prompt (`resources/prompts/docs-system.txt`)

```
You maintain CLAUDE.md — a codebase map read by AI agents before every coding session.
It must stay accurate, concise, and readable in under two minutes.

You receive the current CLAUDE.md and a merged git diff.

Update CLAUDE.md to reflect structural changes from the diff:
- New packages, modules, or major classes added
- New key domain flows or concepts introduced
- New conventions (annotations, patterns, naming rules)
- New dependencies that affect code structure
- Database schema changes (new tables, major column changes)

Rules:
- Only update sections affected by this diff
- Preserve all existing accurate content
- Never include code snippets or implementation details
- Keep all entries concise — this is a map, not documentation
- Return only the complete updated CLAUDE.md. No preamble.
```

### 8.3 Docs agent service

```java
@Service
@RequiredArgsConstructor
public class DocsAgentService {

    private final DocsAiAgent        docsAiAgent;
    private final GitHubClientService gitHubClient;

    @Value("${github.backend-repo}")
    private String backendRepo;

    public void updateDocs(GitHubPrEvent event) {
        String repo     = event.repository().fullName();
        int    prNumber = event.pullRequest().number();

        String diff        = gitHubClient.getPrDiff(repo, prNumber);
        String currentDocs = gitHubClient.getFileContent(repo, "CLAUDE.md");

        if (diff.isBlank()) return;

        String updatedDocs = docsAiAgent.updateDocs(
            "Current CLAUDE.md:\n" + currentDocs + "\n\nMerged diff:\n" + diff);

        gitHubClient.updateFile(repo, "CLAUDE.md", updatedDocs,
            "docs: update CLAUDE.md after PR #" + prNumber);

        log.info("CLAUDE.md updated after PR #{}", prNumber);
    }
}
```

### Initial CLAUDE.md template

Place this in each target repo before the first agent run:

```markdown
# [Project Name] — Codebase map for AI agents

> Maintained automatically by the Documentation Agent.
> Do not edit manually — changes will be overwritten on next PR merge.

## Tech stack
-

## Package structure
-

## Key domain flows
-

## Conventions
-

## Database
-

## Testing
-
```

### Acceptance criteria

- [ ] Webhook receives and verifies GitHub merge events
- [ ] `CLAUDE.md` updated and committed within 2 minutes of a PR merge
- [ ] Webhook signature verification rejects tampered payloads
- [ ] No accurate existing content is removed during updates

---

## Phase 9 — Orchestrator and scheduler

**Goal:** Wire everything together. DB tickets processed before BE tickets. Single health endpoint. Graceful shutdown.

**Deliverables:** `AgentCoordinator`, `HealthController`, final `AgentDevApplication`.

### 9.1 Application entry point

```java
@SpringBootApplication
@EnableScheduling
@EnableRetry
public class AgentDevApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentDevApplication.class, args);
    }
}
```

### 9.2 Agent coordinator

DB changes come before BE because backend code may reference the new schema.

```java
@Component
@RequiredArgsConstructor
public class AgentCoordinator {

    private final JiraClient           jiraClient;
    private final DbAgentService       dbAgentService;
    private final BackendAgentService  backendAgentService;
    private final FrontendAgentService frontendAgentService;

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
            .map(t -> dbPool.submit(() -> dbAgentService.processTicket(t)))
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
```

### 9.3 Health and manual trigger

```java
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final AgentCoordinator agentCoordinator;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "docker", isDockerRunning() ? "UP" : "DOWN",
            "timestamp", Instant.now()
        );
    }

    @PostMapping("/run")
    public ResponseEntity<String> triggerBatch() {
        agentCoordinator.runBatch();
        return ResponseEntity.accepted().body("Batch run started");
    }

    private boolean isDockerRunning() {
        try { return new ProcessBuilder("docker", "info").start().waitFor() == 0; }
        catch (Exception e) { return false; }
    }
}
```

### Acceptance criteria

- [ ] `mvn spring-boot:run` starts with no errors
- [ ] `/api/health` returns `status: UP`
- [ ] DB tickets complete before BE tickets start in a batch
- [ ] Application shuts down gracefully (in-flight tickets finish)

---

## 14. Configuration

### `src/main/resources/application.yml`

```yaml
spring:
  application:
    name: agentic-dev-platform

confluence:
  base-url: ${CONFLUENCE_URL}
  username: ${CONFLUENCE_USERNAME}
  api-token: ${CONFLUENCE_API_TOKEN}

jira:
  base-url: ${JIRA_URL}
  username: ${JIRA_USERNAME}
  api-token: ${JIRA_API_TOKEN}
  project-key: ${JIRA_PROJECT_KEY}

github:
  token: ${GITHUB_TOKEN}
  backend-repo: ${GITHUB_BACKEND_REPO}      # e.g. yourorg/backend-service
  frontend-repo: ${GITHUB_FRONTEND_REPO}    # e.g. yourorg/frontend-app
  webhook-secret: ${GITHUB_WEBHOOK_SECRET}

langchain4j:
  anthropic:
    api-key: ${ANTHROPIC_API_KEY}
    model-name: claude-sonnet-4-20250514
    max-tokens: 8096
    temperature: 0.2
    timeout: PT120S

docker:
  timeout-minutes: 20
  memory-limit: 4g
  cpu-limit: "2.0"
  warmup:
    enabled: true

git:
  workspace-dir: /tmp/agent-workspaces

agent:
  backend:
    poll-interval-ms: 300000
  frontend:
    poll-interval-ms: 300000
  database:
    poll-interval-ms: 300000

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
```

### `.env` (never commit to git)

```bash
ANTHROPIC_API_KEY=sk-ant-...
CONFLUENCE_URL=https://yourorg.atlassian.net
CONFLUENCE_USERNAME=your@email.com
CONFLUENCE_API_TOKEN=...
JIRA_URL=https://yourorg.atlassian.net
JIRA_USERNAME=your@email.com
JIRA_API_TOKEN=...
JIRA_PROJECT_KEY=PROJ
GITHUB_TOKEN=ghp_...
GITHUB_BACKEND_REPO=yourorg/backend-service
GITHUB_FRONTEND_REPO=yourorg/frontend-app
GITHUB_WEBHOOK_SECRET=...
```

---

## 15. Docker setup

### `docker-compose.yml`

```yaml
version: '3.9'

services:

  app:
    build:
      context: .
      dockerfile: docker/Dockerfile.app
    ports:
      - "8080:8080"
    env_file: .env
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock   # spawn Claude Code containers
      - /tmp/agent-workspaces:/tmp/agent-workspaces # shared workspace
    depends_on:
      - prometheus

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"

volumes:
  agent-maven-cache:
    external: true    # docker volume create agent-maven-cache
  agent-npm-cache:
    external: true    # docker volume create agent-npm-cache
```

### One-time setup

```bash
# Build Claude Code agent image
docker build -t claude-code-agent:latest -f docker/Dockerfile .

# Create persistent cache volumes
docker volume create agent-maven-cache
docker volume create agent-npm-cache

# Start everything
docker-compose up --build
```

---

## 16. Testing strategy

### Unit tests — mock at the boundary

```java
@ExtendWith(MockitoExtension.class)
class PlannerServiceTest {

    @Mock ConfluenceClient confluenceClient;
    @Mock JiraClient       jiraClient;
    @Mock PlannerAiAgent   plannerAiAgent;

    @InjectMocks PlannerService plannerService;

    @Test
    void givenValidPage_whenProcessPage_thenTicketsCreatedForAllTypes() {
        when(confluenceClient.getPageContent("PAGE-1")).thenReturn("requirement text");
        when(plannerAiAgent.analyzeRequirements(any())).thenReturn(
            new TicketPlan("PAGE-1", "summary",
                List.of(new JiraTicket("UI form", "...", "...", FRONTEND, 3)),
                List.of(new JiraTicket("POST endpoint", "...", "...", BACKEND, 2)),
                List.of(new JiraTicket("Add table", "...", "...", DATABASE, 1))
            ));
        plannerService.processPage("PAGE-1");
        verify(jiraClient, times(3)).createIssue(any());
    }
}
```

### Integration tests — WireMock for external APIs

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@WireMockTest
class ConfluenceClientIT {

    @Test
    void givenStub_whenGetPageContent_thenReturnsPlainText() {
        stubFor(get(urlPathMatching("/wiki/api/v2/pages/PAGE-1"))
            .willReturn(okJson("""
                {"body":{"storage":{"value":"<p>Hello world</p>"}}}
            """)));
        assertThat(confluenceClient.getPageContent("PAGE-1")).contains("Hello world");
    }
}
```

### End-to-end smoke test (manual, run before each release)

1. Create a JIRA ticket manually: label `agent-be`, summary `Add GET /smoke-test endpoint`
2. Wait for the backend poller (max 5 minutes)
3. Verify: branch created → PR opened → ticket in `In Review`

---

## 17. Implementation milestones

### Milestone 1 — Foundation (Phases 1–4)

All infrastructure working before any agent logic.

- [ ] Models and exceptions compile
- [ ] Confluence, JIRA, GitHub clients return real data
- [ ] Docker container starts, `claude --version` exits 0
- [ ] Maven cache warms — second run is noticeably faster
- [ ] Git clone → branch → diff → commit → push works end-to-end

**Go / no-go:** Run `DockerClaudeRunner` with prompt `"Print hello world"` → get output back.

---

### Milestone 2 — Planner live (Phase 5)

Product person writes in Confluence → tickets appear in JIRA automatically.

- [ ] Planner reads a real Confluence page
- [ ] Correctly typed tickets appear with labels

**Go / no-go:** 3 paragraphs in Confluence → at least 3 JIRA tickets created.

---

### Milestone 3 — First full loop (Phase 6, Backend only)

Prove the ticket → PR pipeline before building FE and DB agents.

- [ ] `BaseAgentService` loop complete
- [ ] `BackendAgentService` and poller working
- [ ] Review agent approves or requests revision
- [ ] PR opened with JIRA key in title

**Go / no-go:** Create `agent-be` ticket manually → PR on GitHub within 15 minutes.

---

### Milestone 4 — All agents (Phase 6 complete)

- [ ] Frontend agent end-to-end
- [ ] Database agent with migration validation

**Go / no-go:** Run planner on a real requirement → all three agent types open PRs.

---

### Milestone 5 — Quality loop (Phases 7–8)

- [ ] Review agent catches and revises incomplete implementations
- [ ] Merged PR → `CLAUDE.md` updated within 2 minutes

**Go / no-go:** Merge a PR → verify CLAUDE.md commit appears on main.

---

### Milestone 6 — Production ready (Phase 9)

- [ ] `docker-compose up` starts everything
- [ ] `/api/health` returns UP
- [ ] DB tickets finish before BE tickets start

**Go / no-go:** Full Confluence page → 3 PRs, fully automated, zero manual steps.

---

*Single Spring Boot module · Java 21 · LangChain4j 0.36.2 · Version 2.0*
