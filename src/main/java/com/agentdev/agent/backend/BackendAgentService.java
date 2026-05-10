package com.agentdev.agent.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.agentdev.agent.base.BaseAgentService;
import com.agentdev.core.model.JiraIssue;

@Service
public class BackendAgentService extends BaseAgentService {

    @Value("${github.backend-repo:yourorg/backend-service}") 
    private String backendRepo;

    @Override protected String getJiraLabel()  { return "agent-be"; }
    @Override protected String getTargetRepo() { return backendRepo; }

    @Override
    protected String buildPrompt(JiraIssue ticket) {
        return String.format("""
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
            """, ticket.key(), ticket.summary(),
                          ticket.description(), ticket.acceptanceCriteria());
    }
}
