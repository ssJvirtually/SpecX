package com.agentdev.docker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.agentdev.core.exception.ClaudeCodeFailedException;
import com.agentdev.core.exception.ClaudeCodeTimeoutException;
import com.agentdev.core.model.ClaudeCodeResult;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

@Component
public class DockerClaudeRunner {

    private static final Logger log = LoggerFactory.getLogger(DockerClaudeRunner.class);

    private static final String IMAGE        = "antigravity-cli";
    private static final String MAVEN_VOLUME = "agent-maven-cache";
    private static final String NPM_VOLUME   = "agent-npm-cache";

    @Value("${anthropic.api-key:dummy}")       private String apiKey;
    @Value("${docker.timeout-minutes:20}")     private int timeoutMinutes;
    @Value("${docker.memory-limit:4g}")        private String memoryLimit;
    @Value("${docker.cpu-limit:2.0}")          private String cpuLimit;

    public ClaudeCodeResult run(String repoPath, String prompt) {
        Instant start = Instant.now();

        String userHome = System.getProperty("user.home");
        List<String> command = List.of(
            "docker", "run", "--rm",
            "-v", repoPath + ":/home/geminiuser/app",
            "-v", userHome + "/.gemini:/home/geminiuser/.gemini",
            "-v", MAVEN_VOLUME + ":/home/geminiuser/.m2",
            "-v", NPM_VOLUME + ":/home/geminiuser/.npm",
            "-w", "/home/geminiuser/app",
            "--network", "bridge",
            "--memory", memoryLimit,
            "--cpus", cpuLimit,
            IMAGE,
            "-p", prompt
        );

        try {
            log.info("Starting Docker ProcessBuilder container command: {}", String.join(" ", command));
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.info("[claude-code] {}", line);
                }
            }

            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new ClaudeCodeTimeoutException(
                    "Exceeded " + timeoutMinutes + " min timeout");
            }

            return new ClaudeCodeResult(
                output.toString(),
                process.exitValue() == 0,
                process.exitValue(),
                Duration.between(start, Instant.now())
            );

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ClaudeCodeFailedException("Container execution failed", e);
        }
    }
}
