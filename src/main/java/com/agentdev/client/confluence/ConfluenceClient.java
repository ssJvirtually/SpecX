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

    public static String resolvePageId(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        input = input.trim();

        // If it's a plain number, it is already a page ID
        if (input.matches("\\d+")) {
            return input;
        }

        // If it is a standard page URL (e.g. /wiki/spaces/SPACE/pages/4128770/Page+Title)
        if (input.contains("/pages/")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("/pages/(\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(input);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        // If it is a tiny URL (e.g. /wiki/x/AgA_ or x/AgA_)
        if (input.contains("/x/")) {
            int idx = input.indexOf("/x/");
            String identifier = input.substring(idx + 3);
            if (identifier.contains("/")) {
                identifier = identifier.substring(0, identifier.indexOf("/"));
            }
            if (identifier.contains("?")) {
                identifier = identifier.substring(0, identifier.indexOf("?"));
            }
            
            try {
                // URL-safe base64 decode
                byte[] bytes = java.util.Base64.getUrlDecoder().decode(identifier);
                long pageId = 0;
                for (int i = 0; i < bytes.length; i++) {
                    pageId |= ((long) (bytes[i] & 0xFF)) << (8 * i);
                }
                return String.valueOf(pageId);
            } catch (Exception e) {
                // Fallback to original identifier if decoding fails
                return identifier;
            }
        }

        return input;
    }

    @Retryable(
        retryFor = { ConfluenceClientException.class, WebClientResponseException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String getPageContent(String pageId) {
        String resolvedPageId = resolvePageId(pageId);
        try {
            ConfluencePage page = webClient.get()
                .uri("/wiki/api/v2/pages/{pageId}?body-format=storage", resolvedPageId)
                .retrieve()
                .bodyToMono(ConfluencePage.class)
                .block();

            if (page == null || page.body() == null || page.body().storage() == null) {
                throw new ConfluenceClientException("Page or body is null for pageId: " + resolvedPageId);
            }
            
            // basic stripping of xhtml
            String content = page.body().storage().value();
            return content.replaceAll("<[^>]*>", "");
        } catch (WebClientResponseException e) {
            throw new ConfluenceClientException("HTTP Error from Confluence: " + e.getStatusCode(), e);
        } catch (Exception e) {
            if (e instanceof ConfluenceClientException) throw e;
            throw new ConfluenceClientException("Failed to fetch page: " + resolvedPageId, e);
        }
    }
}
