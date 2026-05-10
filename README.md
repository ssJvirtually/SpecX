# Agentic Dev Platform

An autonomous, Agentic Software Development Life Cycle (SDLC) Orchestrator powered by LangChain4j, Anthropic Claude, and Docker.

This platform bridges the gap between raw business requirements and tested, validated pull requests. It acts as an automated engineering team that continuously polls for tasks, delegates them to specialized AI agents, and reviews the output before merging.

## 🚀 Features

- **Planner Agent:** Automatically reads Confluence requirement pages and decomposes them into strongly-typed JIRA tickets (Frontend, Backend, Database).
- **Specialized Agents:** Isolated agents dedicated to Frontend, Backend, and Database tasks.
- **Dockerized Execution:** Runs Claude Code CLI inside ephemeral Docker containers, complete with Maven and npm cache warming to accelerate agent performance.
- **Automated Code Review:** A Review Agent independently validates the Claude Code output against the ticket's acceptance criteria, enforcing iterations if the code needs revision.
- **Self-Documenting Codebase:** A Documentation Agent updates the `CLAUDE.md` knowledge map asynchronously upon every merged Pull Request via GitHub webhooks.
- **Robust Orchestration:** Fully automated batch execution ensuring database migrations complete successfully before backend and frontend tasks commence.

## 🏗️ System Architecture

1. **Product Planning:** A Confluence requirement document is parsed by the `Planner Agent` to generate typed JIRA tickets.
2. **Task Polling:** Independent pollers retrieve assigned tickets.
3. **Execution:** Claude Code executes within an isolated Docker container with the full repository context.
4. **Validation:** `Review Agent` inspects the unified Git diff and compares it against acceptance criteria.
5. **Integration:** Approved code is automatically committed and proposed as a GitHub Pull Request.
6. **Maintenance:** Merged PRs trigger the `Docs Agent` to update `CLAUDE.md`.

## 💻 Tech Stack

- **Java 21** & **Spring Boot 3.3.x**
- **LangChain4j** for LLM Orchestration
- **Anthropic Claude Sonnet 3.5** (`claude-sonnet-4-20250514`)
- **Docker** (ProcessBuilder-based isolation)
- **JGit** for source control manipulation
- **Flyway & H2** for in-memory DB migration validation
- **Prometheus & Micrometer** for observability

## ⚙️ Prerequisites

- Java 21
- Maven 3.9+
- Docker Engine
- GitHub, Jira, and Confluence API Tokens
- Anthropic API Key

## 🛠️ Getting Started

### 1. Environment Setup

Create a `.env` file at the root of the project:

```env
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

### 2. Run the Platform via Docker

The platform and its Prometheus metrics server can be launched entirely via Docker Compose.

```bash
docker-compose up --build
```

*Note: The platform mounts `/var/run/docker.sock` to enable the Java application to dynamically spawn and destroy Claude Code worker containers on the host machine.*

### 3. Triggering Workflows

**Health Check:**
```bash
curl http://localhost:8080/api/health
```

**Manual Agentic Loop Trigger:**
```bash
curl -X POST http://localhost:8080/api/run
```

**Planner Trigger (Confluence to Jira):**
```bash
curl -X POST "http://localhost:8080/api/planner/process?confluencePageId=123456"
```

## 🧪 Testing

The codebase relies on strict testing standards for its underlying agents and external integrations.

```bash
mvn clean test
```

* Integration tests leverage **WireMock** to simulate external Jira, Confluence, and GitHub REST APIs.
* Unit testing is powered by **JUnit 5** and **Mockito**.

## 🛡️ License

This project is licensed under the MIT License.
