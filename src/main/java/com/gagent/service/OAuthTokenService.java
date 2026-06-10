package com.gagent.service;

import com.gagent.entity.User;
import com.gagent.exception.ProviderTokenExpiredException;
import com.gagent.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthTokenService {

    private final UserRepository userRepository;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    public GoogleCredential createGoogleCredential(User user) {
        if (user.getGoogleAccessToken() == null || user.getGoogleAccessToken().isBlank()) {
            throw new ProviderTokenExpiredException("google",
                    "Google Workspace is not connected. Please reconnect in Settings.");
        }

        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(new NetHttpTransport())
                .setJsonFactory(GsonFactory.getDefaultInstance())
                .setClientSecrets(googleClientId, googleClientSecret)
                .build()
                .setAccessToken(user.getGoogleAccessToken());

        if (user.getGoogleRefreshToken() != null && !user.getGoogleRefreshToken().isBlank()) {
            credential.setRefreshToken(user.getGoogleRefreshToken());
        }

        return credential;
    }

    public void persistGoogleTokenIfRefreshed(User user, GoogleCredential credential) {
        String newToken = credential.getAccessToken();
        if (newToken != null && !newToken.equals(user.getGoogleAccessToken())) {
            user.setGoogleAccessToken(newToken);
            userRepository.save(user);
            log.info("Persisted refreshed Google access token for user {}", user.getId());
        }
    }

    public void handleGoogleApiError(User user, Exception e) {
        if (e instanceof GoogleJsonResponseException gjre && gjre.getStatusCode() == 401) {
            throw new ProviderTokenExpiredException("google",
                    "Google token expired. Please reconnect Google Workspace in Settings.");
        }
        if (e instanceof IOException ioe && ioe.getCause() instanceof com.google.api.client.auth.oauth2.TokenResponseException tre) {
            if (tre.getDetails() != null && "invalid_grant".equals(tre.getDetails().getError())) {
                throw new ProviderTokenExpiredException("google",
                        "Google token expired. Please reconnect Google Workspace in Settings.");
            }
        }
        if (e.getCause() instanceof com.google.api.client.auth.oauth2.TokenResponseException tre) {
            if (tre.getDetails() != null && "invalid_grant".equals(tre.getDetails().getError())) {
                throw new ProviderTokenExpiredException("google",
                        "Google token expired. Please reconnect Google Workspace in Settings.");
            }
        }
    }

    public boolean isGoogleTokenValid(User user) {
        if (user.getGoogleAccessToken() == null || user.getGoogleAccessToken().isBlank()) {
            return false;
        }
        if (user.getGoogleRefreshToken() == null || user.getGoogleRefreshToken().isBlank()) {
            return false;
        }

        try {
            GoogleCredential credential = createGoogleCredential(user);
            credential.refreshToken();
            persistGoogleTokenIfRefreshed(user, credential);
            return true;
        } catch (IOException e) {
            log.debug("Google token validation failed for user {}: {}", user.getId(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.debug("Google token validation failed for user {}: {}", user.getId(), e.getMessage());
            return false;
        }
    }

    public boolean isGithubTokenValid(User user) {
        if (user.getGithubAccessToken() == null || user.getGithubAccessToken().isBlank()) {
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(user.getGithubAccessToken());
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.github.com/user",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException e) {
            return false;
        } catch (Exception e) {
            log.debug("GitHub token validation failed for user {}: {}", user.getId(), e.getMessage());
            return false;
        }
    }
}
