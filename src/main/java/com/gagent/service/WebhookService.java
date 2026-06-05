package com.gagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gagent.entity.User;
import com.gagent.entity.WebhookEvent;
import com.gagent.repository.UserRepository;
import com.gagent.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.webhook.hmac-algorithm:HmacSHA256}")
    private String hmacAlgorithm;

    public String generateNewSecret(String userId) {
        User user = userRepository.findById(Integer.parseInt(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        SecureRandom random = new SecureRandom();
        byte[] secretBytes = new byte[32];
        random.nextBytes(secretBytes);
        String newSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        user.setWebhookSecret(newSecret);
        userRepository.save(user);
        return newSecret;
    }

    public Optional<String> getSecret(String userId) {
        return userRepository.findById(Integer.parseInt(userId))
                .map(User::getWebhookSecret);
    }

    public List<WebhookEvent> getEvents(String userId) {
        return webhookEventRepository.findByUserIdOrderByReceivedAtDesc(userId);
    }

    public boolean verifySignature(String payload, String signature, String secret) {
        if (secret == null || signature == null) return false;
        try {
            Mac mac = Mac.getInstance(hmacAlgorithm);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), hmacAlgorithm);
            mac.init(secretKey);
            byte[] hash = mac.doFinal(payload.getBytes());
            
            // Format expected signature for GitHub (e.g. "sha256=...")
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String expectedSignature = "sha256=" + hexString.toString();
            
            // Check Jenkins vs Github formatting. If signature doesn't start with sha256=, it might be Jenkins raw hex or base64 token
            // Since we implemented custom header for jenkins or use GitHub's standard, we handle both loosely here.
            if (signature.equals(expectedSignature)) return true;
            if (signature.equals(hexString.toString())) return true;
            if (signature.equals(secret)) return true; // fallback for simple token verification (like Jenkins sometimes uses)

            return false;
        } catch (Exception e) {
            log.error("Error verifying signature", e);
            return false;
        }
    }

    public Long processGithubWebhook(String userId, String payload, String signature) {
        User user = userRepository.findById(Integer.parseInt(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!verifySignature(payload, signature, user.getWebhookSecret())) {
            throw new SecurityException("Invalid webhook signature");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            
            // We only care about workflow_run completed
            if (!root.has("workflow_run")) {
                return null;
            }

            JsonNode workflowRun = root.get("workflow_run");
            String status = workflowRun.has("conclusion") ? workflowRun.get("conclusion").asText() : "unknown";
            
            // Only process failures or errors if we want to filter, but let's record everything that gets here.
            
            WebhookEvent event = WebhookEvent.builder()
                    .userId(userId)
                    .source("github_actions")
                    .repository(root.has("repository") ? root.get("repository").get("full_name").asText() : "unknown")
                    .branch(workflowRun.has("head_branch") ? workflowRun.get("head_branch").asText() : null)
                    .workflowName(workflowRun.has("name") ? workflowRun.get("name").asText() : "unknown")
                    .status(status)
                    .commitSha(workflowRun.has("head_sha") ? workflowRun.get("head_sha").asText().substring(0, Math.min(7, workflowRun.get("head_sha").asText().length())) : null)
                    .commitMessage(workflowRun.has("head_commit") && workflowRun.get("head_commit").has("message") ? 
                            workflowRun.get("head_commit").get("message").asText().split("\n")[0] : null)
                    .runUrl(workflowRun.has("html_url") ? workflowRun.get("html_url").asText() : null)
                    .rawPayload(payload)
                    .build();

            WebhookEvent saved = webhookEventRepository.save(event);
            return saved.getId();
        } catch (Exception e) {
            log.error("Failed to parse GitHub webhook", e);
            throw new RuntimeException("Invalid payload", e);
        }
    }

    public Long processJenkinsWebhook(String userId, String payload, String token) {
        User user = userRepository.findById(Integer.parseInt(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // For Jenkins, we can use a simpler token match if configured that way, or HMAC.
        if (user.getWebhookSecret() == null || !user.getWebhookSecret().equals(token)) {
            throw new SecurityException("Invalid webhook token");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            
            WebhookEvent event = WebhookEvent.builder()
                    .userId(userId)
                    .source("jenkins")
                    .repository(root.has("repository") ? root.get("repository").asText() : "unknown")
                    .branch(root.has("branch") ? root.get("branch").asText() : null)
                    .workflowName(root.has("jobName") ? root.get("jobName").asText() : "unknown")
                    .status(root.has("status") ? root.get("status").asText() : "unknown")
                    .commitSha(root.has("commit") ? root.get("commit").asText().substring(0, Math.min(7, root.get("commit").asText().length())) : null)
                    .commitMessage(root.has("message") ? root.get("message").asText() : null)
                    .runUrl(root.has("buildUrl") ? root.get("buildUrl").asText() : null)
                    .rawPayload(payload)
                    .build();

            WebhookEvent saved = webhookEventRepository.save(event);
            return saved.getId();
        } catch (Exception e) {
            log.error("Failed to parse Jenkins webhook", e);
            throw new RuntimeException("Invalid payload", e);
        }
    }
}
