package com.agentdev.client.confluence.dto;

import java.io.Serializable;

public record ConfluencePage(
    String id,
    String status,
    String title,
    Body body
) implements Serializable {
    public record Body(Storage storage) implements Serializable {}
    public record Storage(String value, String representation) implements Serializable {}
}
