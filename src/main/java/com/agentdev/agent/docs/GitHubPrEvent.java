package com.agentdev.agent.docs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPrEvent(
    String action,
    @JsonProperty("pull_request") PullRequest pullRequest,
    Repository repository
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequest(int number, boolean merged) {}
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repository(@JsonProperty("full_name") String fullName) {}
}
