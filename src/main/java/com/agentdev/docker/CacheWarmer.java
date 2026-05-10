package com.agentdev.docker;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

@Component
public class CacheWarmer {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmer.class);

    @Value("${docker.warmup.enabled:true}")
    private boolean warmupEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!warmupEnabled) return;
        log.info("Pre-warming dependency caches...");

        runAndWait(List.of(
            "docker", "run", "--rm",
            "-v", "agent-maven-cache:/root/.m2",
            "claude-code-agent:latest",
            "mvn", "dependency:get",
            "-Dartifact=org.springframework.boot:spring-boot-starter-web:3.3.2"
        ));

        runAndWait(List.of(
            "docker", "run", "--rm",
            "-v", "agent-npm-cache:/root/.npm",
            "claude-code-agent:latest",
            "npm", "install", "--prefer-offline",
            "react", "react-dom", "typescript", "axios"
        ));

        log.info("Cache warm-up complete");
    }

    private void runAndWait(List<String> command) {
        try {
            new ProcessBuilder(command).start().waitFor(10, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Warm-up step failed (non-fatal): {}", e.getMessage());
        }
    }
}
