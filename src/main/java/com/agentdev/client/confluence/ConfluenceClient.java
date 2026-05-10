package com.agentdev.client.confluence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.agentdev.client.confluence.dto.ConfluencePage;
import com.agentdev.core.exception.ConfluenceClientException;

@Service
public class ConfluenceClient {

    private final WebClient webClient;

    public ConfluenceClient(@Qualifier("confluenceWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Retryable(
        retryFor = { ConfluenceClientException.class, WebClientResponseException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String getPageContent(String pageId) {
        try {
            ConfluencePage page = webClient.get()
                .uri("/wiki/api/v2/pages/{pageId}?body-format=storage", pageId)
                .retrieve()
                .bodyToMono(ConfluencePage.class)
                .block();

            if (page == null || page.body() == null || page.body().storage() == null) {
                throw new ConfluenceClientException("Page or body is null for pageId: " + pageId);
            }
            
            // basic stripping of xhtml
            String content = page.body().storage().value();
            return content.replaceAll("<[^>]*>", "");
        } catch (WebClientResponseException e) {
            throw new ConfluenceClientException("HTTP Error from Confluence: " + e.getStatusCode(), e);
        } catch (Exception e) {
            if (e instanceof ConfluenceClientException) throw e;
            throw new ConfluenceClientException("Failed to fetch page: " + pageId, e);
        }
    }
}
