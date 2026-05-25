package com.agentdev.agent.docs;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/webhooks/github")
public class GitHubWebhookController {

    private final DocsAgentService docsAgentService;
    private final WebhookVerifier  webhookVerifier;
    private final ObjectMapper     objectMapper;

    public GitHubWebhookController(DocsAgentService docsAgentService, WebhookVerifier webhookVerifier, ObjectMapper objectMapper) {
        this.docsAgentService = docsAgentService;
        this.webhookVerifier = webhookVerifier;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Void> handle(
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload) {

        webhookVerifier.verify(payload, signature);

        if ("pull_request".equals(event)) {
            try {
                GitHubPrEvent pr = objectMapper.readValue(payload, GitHubPrEvent.class);
                if ("closed".equals(pr.action()) && pr.pullRequest() != null && pr.pullRequest().merged()) {
                    docsAgentService.updateDocs(pr);
                }
            } catch (Exception e) {
                // Ignore parsing errors for non-matching payloads
            }
        }
        return ResponseEntity.ok().build();
    }
}
