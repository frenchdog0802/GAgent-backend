package com.gagent.controller;

import com.gagent.config.JwtUtil;
import com.gagent.dto.WebhookResponse;
import com.gagent.dto.WebhookSecretResponse;
import com.gagent.entity.WebhookEvent;
import com.gagent.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;
    private final JwtUtil jwtUtil;

    @PostMapping("/{userId}/github")
    public ResponseEntity<WebhookResponse> receiveGithubWebhook(
            @PathVariable String userId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload) {
        
        try {
            Long eventId = webhookService.processGithubWebhook(userId, payload, signature);
            if (eventId == null) {
                // Was not a workflow_run event or something we track
                return ResponseEntity.ok(new WebhookResponse(true, null));
            }
            return ResponseEntity.ok(new WebhookResponse(true, eventId));
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid signature");
        } catch (Exception e) {
            log.error("Error processing github webhook", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error processing payload");
        }
    }

    @PostMapping("/{userId}/jenkins")
    public ResponseEntity<WebhookResponse> receiveJenkinsWebhook(
            @PathVariable String userId,
            @RequestHeader(value = "X-Jenkins-Token", required = false) String token,
            @RequestBody String payload) {
        
        try {
            Long eventId = webhookService.processJenkinsWebhook(userId, payload, token);
            return ResponseEntity.ok(new WebhookResponse(true, eventId));
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        } catch (Exception e) {
            log.error("Error processing jenkins webhook", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error processing payload");
        }
    }

    @GetMapping("/events")
    public ResponseEntity<List<WebhookEvent>> getEvents(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        String userId = getUserIdFromHeader(authHeader);
        return ResponseEntity.ok(webhookService.getEvents(userId));
    }

    @GetMapping("/secret")
    public ResponseEntity<WebhookSecretResponse> getSecret(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        String userId = getUserIdFromHeader(authHeader);
        String secret = webhookService.getSecret(userId).orElse(null);
        return ResponseEntity.ok(new WebhookSecretResponse(secret));
    }

    @PostMapping("/secret/regenerate")
    public ResponseEntity<WebhookSecretResponse> regenerateSecret(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        String userId = getUserIdFromHeader(authHeader);
        String newSecret = webhookService.generateNewSecret(userId);
        return ResponseEntity.ok(new WebhookSecretResponse(newSecret));
    }

    private String getUserIdFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }
        
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }

        return jwtUtil.getUserIdFromToken(token);
    }
}
