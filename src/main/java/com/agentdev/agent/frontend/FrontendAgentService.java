package com.agentdev.agent.frontend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.agentdev.agent.base.BaseAgentService;
import com.agentdev.core.model.JiraIssue;

@Service
public class FrontendAgentService extends BaseAgentService {

    @Value("${github.frontend-repo:yourorg/frontend-app}") 
    private String frontendRepo;

    @Override protected String getJiraLabel()  { return "agent-fe"; }
    @Override protected String getTargetRepo() { return frontendRepo; }

    @Override
    protected String buildPrompt(JiraIssue ticket) {
        return String.format("""
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
            """, ticket.key(), ticket.summary(),
                          ticket.description(), ticket.acceptanceCriteria());
    }
}
