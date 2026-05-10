package com.agentdev.orchestrator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final AgentCoordinator agentCoordinator;

    public HealthController(AgentCoordinator agentCoordinator) {
        this.agentCoordinator = agentCoordinator;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "docker", isDockerRunning() ? "UP" : "DOWN",
            "timestamp", Instant.now()
        );
    }

    @PostMapping("/run")
    public ResponseEntity<String> triggerBatch() {
        agentCoordinator.runBatch();
        return ResponseEntity.accepted().body("Batch run started");
    }

    private boolean isDockerRunning() {
        try { 
            return new ProcessBuilder("docker", "info").start().waitFor() == 0; 
        } catch (Exception e) { 
            return false; 
        }
    }
}
