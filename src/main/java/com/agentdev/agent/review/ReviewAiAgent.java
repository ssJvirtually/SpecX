package com.agentdev.agent.review;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import com.agentdev.core.model.ReviewDecision;

@AiService
public interface ReviewAiAgent {

    @SystemMessage(fromResource = "prompts/review-system.txt")
    ReviewDecision review(@UserMessage String ticketAndDiff);
}
