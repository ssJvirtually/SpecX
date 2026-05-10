package com.agentdev.client.github.dto;

import java.io.Serializable;

public record PullRequestRequest(
    String title,
    String head,
    String base,
    String body
) implements Serializable {}
