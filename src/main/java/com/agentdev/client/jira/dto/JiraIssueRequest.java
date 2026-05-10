package com.agentdev.client.jira.dto;

import java.io.Serializable;
import java.util.List;

public record JiraIssueRequest(
    String summary,
    String description,
    List<String> labels,
    int storyPoints
) implements Serializable {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String summary;
        private String description;
        private List<String> labels;
        private int storyPoints;

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder labels(List<String> labels) {
            this.labels = labels;
            return this;
        }

        public Builder storyPoints(int storyPoints) {
            this.storyPoints = storyPoints;
            return this;
        }

        public JiraIssueRequest build() {
            return new JiraIssueRequest(summary, description, labels, storyPoints);
        }
    }
}

