package com.agentdev.core.model;

import java.io.Serializable;
import java.time.Duration;

public record ClaudeCodeResult(
    String rawOutput,
    boolean success,
    int exitCode,
    Duration duration
) implements Serializable {}
