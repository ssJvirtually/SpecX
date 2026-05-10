package com.agentdev.agent.docs;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface DocsAiAgent {

    @SystemMessage(fromResource = "prompts/docs-system.txt")
    String updateDocs(@UserMessage String currentDocsAndDiff);
}
