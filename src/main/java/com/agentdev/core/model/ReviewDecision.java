package com.agentdev.core.model;

import java.io.Serializable;
import java.util.List;

public record ReviewDecision(
    ReviewStatus status,
    String summary,
    List<String> issues,
    List<String> suggestions
) implements Serializable {}
