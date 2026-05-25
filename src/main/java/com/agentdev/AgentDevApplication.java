package com.agentdev;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = { FlywayAutoConfiguration.class })
@EnableScheduling
@EnableRetry
public class AgentDevApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentDevApplication.class, args);
    }
}
