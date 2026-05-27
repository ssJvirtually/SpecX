package com.agentdev.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.core.command.WaitContainerResultCallback;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

    private static final String IMAGE = "antigravity-cli";

    @Value("${docker.timeout-minutes:20}")     private int timeoutMinutes;
    @Value("${docker.memory-limit:4g}")        private String memoryLimit;
    @Value("${docker.cpu-limit:2.0}")          private String cpuLimit;

    public ClaudeCodeResult run(String repoPath, String prompt) {
        Instant start = Instant.now();
        DockerClient dockerClient = null;
        String containerId = null;

        try {
            log.info("Starting Docker Java Client initialization...");
            String userHome = System.getProperty("user.home");
            String geminiHostPath = Paths.get(userHome, ".gemini").toAbsolutePath().toString();

            log.info("Step 1: Creating DefaultDockerClientConfig...");
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            log.info("Docker daemon URI configured: {}", config.getDockerHost());

            log.info("Step 2: Creating ZerodepDockerHttpClient.Builder...");
            DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .maxConnections(100)
                    .connectionTimeout(Duration.ofSeconds(30))
                    .responseTimeout(Duration.ofSeconds(45))
                    .build();
            log.info("ZerodepDockerHttpClient constructed successfully.");

            log.info("Step 3: Instantiating DockerClient via DockerClientBuilder with Zerodep transport...");
            dockerClient = DockerClientBuilder.getInstance(config)
                    .withDockerHttpClient(httpClient)
                    .build();
            log.info("Step 4: DockerClient initialized successfully.");

            // Configure volume binds (Host project folder -> Container workspace)
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withBinds(
                            new Bind(repoPath, new Volume("/workspace")),
                            new Bind(geminiHostPath, new Volume("/home/geminiuser/.gemini")),
                            new Bind("agent-maven-cache", new Volume("/home/geminiuser/.m2")),
                            new Bind("agent-npm-cache", new Volume("/home/geminiuser/.npm"))
                    );

            log.info("Creating Antigravity CLI container in the foreground...");
            CreateContainerResponse container = dockerClient.createContainerCmd(IMAGE)
                    .withHostConfig(hostConfig)
                    .withWorkingDir("/workspace")
                    .withCmd("agy", "--dangerously-skip-permissions", "-p", prompt)
                    .exec();

            containerId = container.getId();
            log.info("Container created successfully. ID: {}", containerId);

            log.info("Starting container...");
            dockerClient.startContainerCmd(containerId).exec();
            log.info("Container successfully started. Streaming logs in real-time...");

            LoggingOutputStream logStream = new LoggingOutputStream();
            
            // Attach log listener to stream container logs
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .withTailAll()
                    .exec(new LogContainerResultCallback() {
                        @Override
                        public void onNext(Frame item) {
                            try {
                                logStream.write(item.getPayload());
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                    });

            // Wait for container to exit completely
            log.info("Waiting for Antigravity container to exit...");
            boolean completed = dockerClient.waitContainerCmd(containerId)
                    .exec(new WaitContainerResultCallback())
                    .awaitCompletion(timeoutMinutes, TimeUnit.MINUTES);

            if (!completed) {
                throw new ClaudeCodeTimeoutException("Antigravity execution exceeded " + timeoutMinutes + " minutes timeout");
            }

            log.info("Antigravity execution completed successfully.");
            return new ClaudeCodeResult(
                logStream.getOutput(),
                true,
                0,
                Duration.between(start, Instant.now())
            );

        } catch (Exception e) {
            log.error("Antigravity container execution failed", e);
            throw new ClaudeCodeFailedException("Container execution failed", e);
        } finally {
            if (dockerClient != null && containerId != null) {
                try {
                    log.info("Removing Antigravity container...");
                    dockerClient.removeContainerCmd(containerId).withForce(true).exec();
                } catch (Exception e) {
                    log.warn("Failed to remove container: {}", e.getMessage());
                }

                try {
                    dockerClient.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private static class LoggingOutputStream extends OutputStream {
        private final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();

        @Override
        public void write(int b) throws java.io.IOException {
            bos.write(b);
            if (b == '\n') {
                String line = lineBuffer.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
                if (!line.isEmpty()) {
                    log.info("[antigravity] {}", line);
                }
                lineBuffer.reset();
            } else if (b != '\r') {
                lineBuffer.write(b);
            }
        }

        public String getOutput() {
            return bos.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
