package com.agentdev.client.jira;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.agentdev.client.jira.dto.JiraIssueRequest;
import com.agentdev.client.jira.dto.JiraIssueResponse;
import com.agentdev.core.exception.JiraClientException;
import com.agentdev.core.model.JiraIssue;
import com.agentdev.core.model.TicketStatus;
import com.agentdev.core.model.TicketType;

@Service
public class JiraClient {

    private final WebClient webClient;

    @Value("${jira.project-key:PSPKC}")
    private String projectKey;

    public JiraClient(@Qualifier("jiraWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Retryable(
        retryFor = { JiraClientException.class, WebClientResponseException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String createIssue(JiraIssueRequest request) {
        try {
            JiraIssueResponse response = webClient.post()
                .uri("/rest/api/3/issue")
                .bodyValue(Map.of(
                    "fields", Map.of(
                        "summary", request.summary(),
                        "description", Map.of(
                            "type", "doc",
                            "version", 1,
                            "content", List.of(Map.of(
                                "type", "paragraph",
                                "content", List.of(Map.of(
                                    "type", "text",
                                    "text", request.description()
                                ))
                            ))
                        ),
                        "project", Map.of("key", projectKey),
                        "issuetype", Map.of("name", "Task"),
                        "labels", request.labels()
                    )
                ))
                .retrieve()
                .bodyToMono(JiraIssueResponse.class)
                .block();

            return response != null ? response.key() : null;
        } catch (Exception e) {
            throw new JiraClientException("Failed to create issue", e);
        }
    }

    @Retryable(
        retryFor = { JiraClientException.class, WebClientResponseException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public List<JiraIssue> searchIssues(String jql) {
        try {
            Map<String, Object> response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/rest/api/3/search")
                    .queryParam("jql", jql)
                    .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

            if (response == null || !response.containsKey("issues")) {
                return List.of();
            }

            List<Map<String, Object>> issues = (List<Map<String, Object>>) response.get("issues");
            return issues.stream().map(issue -> {
                Map<String, Object> fields = (Map<String, Object>) issue.get("fields");
                String key = (String) issue.get("key");
                String summary = (String) fields.get("summary");
                String description = ""; 
                
                TicketStatus status = TicketStatus.TODO; 
                TicketType type = TicketType.BACKEND; 

                return new JiraIssue(key, summary, description, "", type, status);
            }).collect(Collectors.toList());
        } catch (Exception e) {
            throw new JiraClientException("Failed to search issues", e);
        }
    }

    public void transitionIssue(String issueKey, String transitionName) {
        try {
            // Simplified placeholder for transition
        } catch (Exception e) {
            throw new JiraClientException("Failed to transition issue: " + issueKey, e);
        }
    }

    public void addComment(String issueKey, String body) {
        try {
            webClient.post()
                .uri("/rest/api/3/issue/{key}/comment", issueKey)
                .bodyValue(Map.of(
                    "body", Map.of(
                        "type", "doc",
                        "version", 1,
                        "content", List.of(Map.of(
                            "type", "paragraph",
                            "content", List.of(Map.of(
                                "type", "text",
                                "text", body
                            ))
                        ))
                    )
                ))
                .retrieve()
                .toBodilessEntity()
                .block();
        } catch (Exception e) {
            throw new JiraClientException("Failed to add comment to: " + issueKey, e);
        }
    }
}
