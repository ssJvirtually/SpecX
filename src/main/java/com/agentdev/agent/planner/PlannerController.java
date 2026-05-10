package com.agentdev.agent.planner;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planner")
public class PlannerController {

    private final PlannerService plannerService;

    public PlannerController(PlannerService plannerService) {
        this.plannerService = plannerService;
    }

    @PostMapping("/process")
    public ResponseEntity<String> process(@RequestParam String confluencePageId) {
        plannerService.processPage(confluencePageId);
        return ResponseEntity.accepted()
            .body("Processing started for page: " + confluencePageId);
    }
}
