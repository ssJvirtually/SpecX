package com.agentdev.agent.review;

import org.springframework.stereotype.Service;

import com.agentdev.core.model.JiraIssue;
import com.agentdev.core.model.ReviewDecision;
import com.agentdev.core.model.ReviewStatus;

import java.util.List;

@Service
public class ReviewService {

    private final ReviewAiAgent reviewAiAgent;
    private static final int MAX_DIFF_CHARS = 50_000;

    public ReviewService(ReviewAiAgent reviewAiAgent) {
        this.reviewAiAgent = reviewAiAgent;
    }

    public ReviewDecision review(JiraIssue ticket, String diff) {
        if (diff.isBlank()) {
            return new ReviewDecision(ReviewStatus.NEEDS_REVISION,
                "No changes detected",
                List.of("Claude Code produced no file changes"),
                List.of("Re-implement the ticket. Ensure files are written to /workspace."));
        }

        String trimmedDiff = diff.length() > MAX_DIFF_CHARS
            ? diff.substring(0, MAX_DIFF_CHARS) + "\n... [truncated]"
            : diff;

        String prompt = String.format("""
            JIRA Ticket : %s
            Summary     : %s

            Description:
            %s

            Acceptance Criteria:
            %s

            Git Diff:
            %s
            """, ticket.key(), ticket.summary(),
                 ticket.description(), ticket.acceptanceCriteria(), trimmedDiff);

        return reviewAiAgent.review(prompt);
    }
}
