package com.agentdev;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;
import java.nio.file.Files;

@SpringBootApplication(exclude = { FlywayAutoConfiguration.class })
@EnableScheduling
@EnableRetry
public class AgentDevApplication {

    static {
        try {
            File envFile = new File(".env");
            if (envFile.exists()) {
                Files.lines(envFile.toPath())
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(line -> {
                        int delimiterIdx = line.indexOf('=');
                        if (delimiterIdx > 0) {
                            String key = line.substring(0, delimiterIdx).trim();
                            String value = line.substring(delimiterIdx + 1).trim();
                            if ((value.startsWith("\"") && value.endsWith("\"")) || 
                                (value.startsWith("'") && value.endsWith("'"))) {
                                value = value.substring(1, value.length() - 1);
                            }
                            System.setProperty(key, value);
                        }
                    });
                System.out.println("Loaded environment variables from .env into JVM System properties successfully.");
            } else {
                System.out.println(".env file not found in current directory. Using default/system environment.");
            }
        } catch (Exception e) {
            System.err.println("Failed to load environment variables from .env: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(AgentDevApplication.class, args);
    }
}
