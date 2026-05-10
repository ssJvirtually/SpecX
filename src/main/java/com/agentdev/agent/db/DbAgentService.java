package com.agentdev.agent.db;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.agentdev.agent.base.BaseAgentService;
import com.agentdev.core.exception.AgentException;
import com.agentdev.core.model.JiraIssue;

@Service
public class DbAgentService extends BaseAgentService {

    @Value("${github.backend-repo:yourorg/backend-service}") 
    private String backendRepo;
    
    @Autowired 
    private MigrationValidator migrationValidator;

    @Override protected String getJiraLabel()  { return "agent-db"; }
    @Override protected String getTargetRepo() { return backendRepo; }

    @Override
    protected void postProcess(String repoPath, JiraIssue ticket) {
        String migrationsPath = repoPath + "/src/main/resources/db/migration";
        MigrationValidator.ValidationResult result = migrationValidator.validate(migrationsPath);
        if (!result.valid()) {
            throw new AgentException(
                "Migration validation failed for " + ticket.key() + ": " + result.error());
        }
    }

    @Override
    protected String buildPrompt(JiraIssue ticket) {
        return String.format("""
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
            """, ticket.key(), ticket.summary(),
                          ticket.description(), ticket.acceptanceCriteria());
    }
}
