package com.agentdev.client.confluence;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ConfluenceConfig {

    @Bean
    public WebClient confluenceWebClient(
            @Value("${confluence.base-url}") String baseUrl,
            @Value("${confluence.username}") String username,
            @Value("${confluence.api-token}") String token) {
        String credentials = Base64.getEncoder()
            .encodeToString((username + ":" + token).getBytes());
        return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}
