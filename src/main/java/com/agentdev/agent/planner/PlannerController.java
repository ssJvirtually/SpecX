package com.agentdev.agent.planner;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.agentdev.orchestrator.AgentCoordinator;

@RestController
@RequestMapping("/api/planner")
public class PlannerController {

    private final AgentCoordinator agentCoordinator;

    public PlannerController(AgentCoordinator agentCoordinator) {
        this.agentCoordinator = agentCoordinator;
    }

    @PostMapping("/process")
    public ResponseEntity<String> process(@RequestParam String confluencePageId) {
        agentCoordinator.orchestrateAsync(confluencePageId);
        return ResponseEntity.accepted()
            .body("Linear orchestration pipeline started in background for page: " + confluencePageId);
    }
}
