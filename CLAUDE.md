# Agentic Dev Platform — Codebase map for AI agents

> Maintained automatically by the Documentation Agent.
> Do not edit manually — changes will be overwritten on next PR merge.

## Tech stack
- Java 21, Spring Boot 3.3.x, Maven 3.9.x
- LangChain4j for AI integration with Anthropic Claude

## Package structure
- `com.agentdev.core` - Shared models and exceptions
- `com.agentdev.client` - External API wrappers (Confluence, Jira, GitHub)
- `com.agentdev.git` - JGit operations
- `com.agentdev.docker` - Docker infrastructure and runner for Claude Code
- `com.agentdev.agent` - Agent services (Planner, Backend, Frontend, DB, Review, Docs)
- `com.agentdev.orchestrator` - Agent coordinator and health controller

## Key domain flows
- Planner -> Read Confluence -> Create JIRA Tickets
- Pollers -> Pick JIRA Tickets -> Execute Claude Code in Docker -> Review -> Open PR

## Conventions
- Use pure Java records and enums for core models.
- All external calls must have retry logic.

## Database
- Flyway migrations executed and validated via H2 in-memory DB.

## Testing
- JUnit 5 and Mockito for unit testing.
- WireMock for external client integration tests.
