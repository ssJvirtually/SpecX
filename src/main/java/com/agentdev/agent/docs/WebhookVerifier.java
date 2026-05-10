package com.agentdev.agent.docs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.agentdev.core.exception.AgentException;

@Component
public class WebhookVerifier {

    @Value("${github.webhook-secret:}")
    private String secret;

    public void verify(String payload, String signature) {
        if (secret == null || secret.isEmpty()) return;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes());
            
            StringBuilder expectedSignature = new StringBuilder("sha256=");
            for (byte b : hash) {
                expectedSignature.append(String.format("%02x", b));
            }

            if (!expectedSignature.toString().equals(signature)) {
                throw new AgentException("Webhook signature verification failed");
            }
        } catch (Exception e) {
            throw new AgentException("Webhook signature verification error", e);
        }
    }
}
