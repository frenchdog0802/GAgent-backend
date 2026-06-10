package com.gagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gagent.entity.CiWorkflow;
import com.gagent.entity.CiWorkflowStatus;
import com.gagent.entity.User;
import com.gagent.entity.WebhookEvent;
import com.gagent.repository.CiWorkflowRepository;
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
    private final CiWorkflowRepository ciWorkflowRepository;
    private final CiRemediationService ciRemediationService;
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

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String expectedSignature = "sha256=" + hexString.toString();

            if (signature.equals(expectedSignature)) return true;
            if (signature.equals(hexString.toString())) return true;
            if (signature.equals(secret)) return true;

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

            if (!root.has("workflow_run") || !root.has("action") || !root.get("action").asText().equals("completed")) {
                return null;
            }

            JsonNode workflowRun = root.get("workflow_run");
            String conclusion = workflowRun.has("conclusion") ? workflowRun.get("conclusion").asText() : "unknown";
            String repository = root.has("repository") ? root.get("repository").get("full_name").asText() : "unknown";
            String fullSha = workflowRun.has("head_sha") ? workflowRun.get("head_sha").asText() : null;
            Long runId = workflowRun.has("id") ? workflowRun.get("id").asLong() : null;

            WebhookEvent event = WebhookEvent.builder()
                    .userId(userId)
                    .source("github_actions")
                    .repository(repository)
                    .branch(workflowRun.has("head_branch") ? workflowRun.get("head_branch").asText() : null)
                    .workflowName(workflowRun.has("name") ? workflowRun.get("name").asText() : "unknown")
                    .status(conclusion)
                    .commitSha(fullSha != null ? fullSha.substring(0, Math.min(7, fullSha.length())) : null)
                    .commitMessage(workflowRun.has("head_commit") && workflowRun.get("head_commit").has("message") ?
                            workflowRun.get("head_commit").get("message").asText().split("\n")[0] : null)
                    .runUrl(workflowRun.has("html_url") ? workflowRun.get("html_url").asText() : null)
                    .rawPayload(payload)
                    .build();

            WebhookEvent saved = webhookEventRepository.save(event);

            if ("failure".equals(conclusion) || "timed_out".equals(conclusion)) {
                String branch = workflowRun.has("head_branch") ? workflowRun.get("head_branch").asText() : null;
                triggerRemediation(user, saved, repository, fullSha, runId, branch);
            }

            return saved.getId();
        } catch (Exception e) {
            log.error("Failed to parse GitHub webhook", e);
            throw new RuntimeException("Invalid payload", e);
        }
    }

    private void triggerRemediation(User user, WebhookEvent event, String repository, String fullSha,
            Long runId, String branch) {
        if (user.getGithubAccessToken() == null || user.getGithubAccessToken().isBlank()) {
            log.warn("Skipping remediation for event {} — user {} has no GitHub token", event.getId(), user.getId());
            return;
        }

        if (ciWorkflowRepository.findByWebhookEventId(event.getId()).isPresent()) {
            log.info("Remediation already exists for webhook event {}", event.getId());
            return;
        }

        String cloneUrl = "https://github.com/" + repository + ".git";

        CiWorkflow workflow = CiWorkflow.builder()
                .userId(user.getId())
                .webhookEventId(event.getId())
                .repository(repository)
                .branch(branch)
                .cloneUrl(cloneUrl)
                .failingSha(fullSha != null ? fullSha : "HEAD")
                .failingShaFull(fullSha)
                .githubRunId(runId)
                .status(CiWorkflowStatus.PENDING)
                .testCommand("make test")
                .build();

        CiWorkflow saved = ciWorkflowRepository.save(workflow);
        log.info("Created CiWorkflow {} for failed run on {}", saved.getId(), repository);
        ciRemediationService.startAsync(saved.getId(), user.getGithubAccessToken());
    }

    public Long processJenkinsWebhook(String userId, String payload, String token) {
        User user = userRepository.findById(Integer.parseInt(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

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
