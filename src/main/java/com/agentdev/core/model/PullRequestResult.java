package com.agentdev.core.model;

import java.io.Serializable;

public record PullRequestResult(
    String url,
    int number,
    String branchName,
    String jiraKey
) implements Serializable {}
