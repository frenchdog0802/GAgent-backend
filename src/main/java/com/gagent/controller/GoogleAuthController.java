package com.gagent.controller;

import com.gagent.entity.User;
import com.gagent.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/google")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class GoogleAuthController {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/connect")
    public Map<String, String> connect(HttpServletRequest request, @RequestParam String userId) {
        String redirectUri = buildRedirectUri(request);
        String scope = "openid email profile https://www.googleapis.com/auth/gmail.send https://www.googleapis.com/auth/gmail.readonly";

        String url = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", scope)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", userId)
                .build().toUriString();

        return Map.of("url", url);
    }

    @GetMapping("/callback")
    public void callback(HttpServletRequest request, @RequestParam String code, @RequestParam String state,
            HttpServletResponse response) throws IOException {
        String redirectUri = buildRedirectUri(request);

        // Exchange code for tokens
        String tokenUrl = "https://oauth2.googleapis.com/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("code", code);
        map.add("client_id", clientId);
        map.add("client_secret", clientSecret);
        map.add("redirect_uri", redirectUri);
        map.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> tokenRequest = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(tokenUrl, tokenRequest, Map.class);

            if (tokenResponse.getStatusCode() == HttpStatus.OK && tokenResponse.getBody() != null) {
                Map<String, Object> body = tokenResponse.getBody();
                String accessToken = (String) body.get("access_token");
                String refreshToken = (String) body.get("refresh_token");

                // Update user record
                Integer userId = Integer.parseInt(state);
                userRepository.findById(userId).ifPresent(user -> {
                    user.setGoogleAccessToken(accessToken);
                    if (refreshToken != null) {
                        user.setGoogleRefreshToken(refreshToken);
                    }
                    userRepository.save(user);
                    log.info("Successfully updated Google tokens for user {}", userId);
                });

                response.sendRedirect(frontendUrl + "/settings?connected=true");
            } else {
                log.error("Failed to exchange code for tokens: {}", tokenResponse.getStatusCode());
                response.sendRedirect(frontendUrl + "/settings?connected=false&error=token_exchange_failed");
            }
        } catch (Exception e) {
            log.error("Error during Google OAuth callback", e);
            response.sendRedirect(frontendUrl + "/settings?connected=false&error=" + e.getMessage());
        }
    }

    private String buildRedirectUri(HttpServletRequest request) {
        // Dynamically build the redirect URI based on the current request host and port
        return UriComponentsBuilder.fromUriString(request.getRequestURL().toString())
                .replacePath("/api/auth/google/callback")
                .replaceQuery(null)
                .build().toUriString();
    }
}
