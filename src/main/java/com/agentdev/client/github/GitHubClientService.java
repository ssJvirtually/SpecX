package com.agentdev.client.github;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.agentdev.client.github.dto.PullRequestRequest;
import com.agentdev.core.exception.AgentException;
import com.agentdev.core.model.PullRequestResult;

import java.util.Map;
import java.util.Base64;

@Service
public class GitHubClientService {

    private final WebClient webClient;

    public GitHubClientService(@Qualifier("gitHubWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Retryable(
        retryFor = { AgentException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public PullRequestResult openPullRequest(String repoFullName, String title,
                                             String sourceBranch, String body) {
        try {
            Map<String, Object> response = webClient.post()
                .uri("/repos/{repo}/pulls", repoFullName)
                .bodyValue(new PullRequestRequest(title, sourceBranch, "main", body))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            if (response == null) throw new AgentException("Empty response from GitHub");

            String url = (String) response.get("html_url");
            Integer number = (Integer) response.get("number");

            return new PullRequestResult(url, number, sourceBranch, extractJiraKey(title));
        } catch (Exception e) {
            throw new AgentException("Failed to open PR", e);
        }
    }

    private String extractJiraKey(String title) {
        if (title != null && title.contains(":")) {
            return title.split(":")[0].trim();
        }
        return "UNKNOWN";
    }

    public String getFileContent(String repoFullName, String filePath) {
        try {
            Map<String, Object> response = webClient.get()
                .uri("/repos/{repo}/contents/{path}", repoFullName, filePath)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            if (response == null || !response.containsKey("content")) return "";
            String base64Content = (String) response.get("content");
            return new String(Base64.getMimeDecoder().decode(base64Content.replace("\n", "")));
        } catch (Exception e) {
            return "";
        }
    }

    public void updateFile(String repoFullName, String filePath,
                           String content, String commitMessage) {
        try {
            String sha = getFileSha(repoFullName, filePath);
            
            webClient.put()
                .uri("/repos/{repo}/contents/{path}", repoFullName, filePath)
                .bodyValue(Map.of(
                    "message", commitMessage,
                    "content", Base64.getEncoder().encodeToString(content.getBytes()),
                    "sha", sha != null ? sha : ""
                ))
                .retrieve()
                .toBodilessEntity()
                .block();
        } catch (Exception e) {
            throw new AgentException("Failed to update file: " + filePath, e);
        }
    }

    private String getFileSha(String repoFullName, String filePath) {
        try {
            Map<String, Object> response = webClient.get()
                .uri("/repos/{repo}/contents/{path}", repoFullName, filePath)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            return response != null ? (String) response.get("sha") : null;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean branchExists(String repoFullName, String branchName) {
        try {
            webClient.get()
                .uri("/repos/{repo}/branches/{branch}", repoFullName, branchName)
                .retrieve()
                .toBodilessEntity()
                .block();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getPrDiff(String repoFullName, int prNumber) {
        try {
            return webClient.get()
                .uri("/repos/{repo}/pulls/{number}", repoFullName, prNumber)
                .header("Accept", "application/vnd.github.v3.diff")
                .retrieve()
                .bodyToMono(String.class)
                .block();
        } catch (Exception e) {
            throw new AgentException("Failed to get PR diff for PR #" + prNumber, e);
        }
    }
}
