package com.agentdev.agent.docs;

import org.springframework.stereotype.Service;

import com.agentdev.client.github.GitHubClientService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DocsAgentService {

    private static final Logger log = LoggerFactory.getLogger(DocsAgentService.class);

    private final DocsAiAgent        docsAiAgent;
    private final GitHubClientService gitHubClient;

    public DocsAgentService(DocsAiAgent docsAiAgent, GitHubClientService gitHubClient) {
        this.docsAiAgent = docsAiAgent;
        this.gitHubClient = gitHubClient;
    }

    public void updateDocs(GitHubPrEvent event) {
        String repo     = event.repository().fullName();
        int    prNumber = event.pullRequest().number();

        String diff        = gitHubClient.getPrDiff(repo, prNumber);
        String currentDocs = gitHubClient.getFileContent(repo, "CLAUDE.md");

        if (diff == null || diff.isBlank()) return;

        String updatedDocs = docsAiAgent.updateDocs(
            "Current CLAUDE.md:\n" + currentDocs + "\n\nMerged diff:\n" + diff);

        gitHubClient.updateFile(repo, "CLAUDE.md", updatedDocs,
            "docs: update CLAUDE.md after PR #" + prNumber);

        log.info("CLAUDE.md updated after PR #{}", prNumber);
    }
}
