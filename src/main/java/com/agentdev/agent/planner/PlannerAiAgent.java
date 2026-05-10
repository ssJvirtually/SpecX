package com.agentdev.agent.planner;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import com.agentdev.core.model.TicketPlan;

@AiService
public interface PlannerAiAgent {

    @SystemMessage(fromResource = "prompts/planner-system.txt")
    TicketPlan analyzeRequirements(@UserMessage String requirementsText);
}
