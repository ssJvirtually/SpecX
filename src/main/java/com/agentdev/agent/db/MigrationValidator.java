package com.agentdev.agent.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.springframework.stereotype.Component;

@Component
public class MigrationValidator {

    public ValidationResult validate(String migrationsPath) {
        try {
            Flyway.configure()
                .dataSource("jdbc:h2:mem:validation_"
                    + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1", "sa", "")
                .locations("filesystem:" + migrationsPath)
                .load()
                .migrate();
            return ValidationResult.success();
        } catch (FlywayException e) {
            return ValidationResult.invalid(e.getMessage());
        }
    }

    public record ValidationResult(boolean valid, String error) {
        static ValidationResult success()             { return new ValidationResult(true, null); }
        static ValidationResult invalid(String err) { return new ValidationResult(false, err); }
    }
}
