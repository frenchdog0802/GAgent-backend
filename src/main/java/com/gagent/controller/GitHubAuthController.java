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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/github")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class GitHubAuthController {

    @Value("${app.github.client-id:}")
    private String clientId;

    @Value("${app.github.client-secret:}")
    private String clientSecret;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.backend-url}")
    private String backendUrl;

    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/connect")
    public ResponseEntity<Map<String, String>> connect(HttpServletRequest request, @RequestParam String userId) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "GitHub OAuth is not configured. Set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET."));
        }

        if (clientId.matches("\\d+")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "GITHUB_CLIENT_ID looks like a GitHub App ID (numeric). Use the OAuth Client ID from your app settings — it starts with Iv23 or Ov23, not the numeric App ID."));
        }
        if (clientSecret.startsWith("Iv") || clientSecret.startsWith("Ov")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "GITHUB_CLIENT_SECRET looks like a Client ID, not a secret. Generate a new client secret in your GitHub OAuth App settings and put the Client ID in GITHUB_CLIENT_ID."));
        }

        String redirectUri = buildRedirectUri(request);
        String scope = "repo user:email";

        String url = UriComponentsBuilder.fromUriString("https://github.com/login/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", scope)
                .queryParam("state", userId)
                .build().toUriString();

        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/callback")
    public void callback(HttpServletRequest request, @RequestParam String code, @RequestParam String state,
            HttpServletResponse response) throws IOException {
        String redirectUri = buildRedirectUri(request);
        String tokenUrl = "https://github.com/login/oauth/access_token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("code", code);
        map.add("client_id", clientId);
        map.add("client_secret", clientSecret);
        map.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> tokenRequest = new HttpEntity<>(map, headers);

        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> tokenResponse =
                    (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.postForEntity(tokenUrl, tokenRequest, Map.class);

            if (tokenResponse.getStatusCode() == HttpStatus.OK && tokenResponse.getBody() != null) {
                Map<String, Object> body = tokenResponse.getBody();
                String accessToken = (String) body.get("access_token");

                if (accessToken == null) {
                    log.error("GitHub token exchange returned no access_token");
                    response.sendRedirect(frontendUrl + "/settings?github=error&error=token_exchange_failed");
                    return;
                }

                Integer userId = Integer.parseInt(state);
                User user = userRepository.findById(userId).orElse(null);
                if (user == null) {
                    response.sendRedirect(frontendUrl + "/settings?github=error&error=user_not_found");
                    return;
                }

                String githubLogin = fetchGithubLogin(accessToken);
                user.setGithubAccessToken(accessToken);
                user.setGithubLogin(githubLogin);
                userRepository.save(user);
                log.info("Successfully connected GitHub for user {} (login={})", userId, githubLogin);

                response.sendRedirect(frontendUrl + "/settings?github=connected");
            } else {
                log.error("Failed to exchange GitHub code for tokens: {}", tokenResponse.getStatusCode());
                response.sendRedirect(frontendUrl + "/settings?github=error&error=token_exchange_failed");
            }
        } catch (Exception e) {
            log.error("Error during GitHub OAuth callback", e);
            response.sendRedirect(frontendUrl + "/settings?github=error&error=callback_failed");
        }
    }

    private String fetchGithubLogin(String accessToken) {
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(accessToken);
        authHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> userRequest = new HttpEntity<>(authHeaders);

        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> userResponse =
                (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.exchange(
                "https://api.github.com/user",
                HttpMethod.GET,
                userRequest,
                Map.class);

        if (userResponse.getBody() != null && userResponse.getBody().get("login") != null) {
            return userResponse.getBody().get("login").toString();
        }
        return null;
    }

    private String buildRedirectUri(HttpServletRequest request) {
        return backendUrl.replaceAll("/+$", "") + "/api/auth/github/callback";
    }
}
