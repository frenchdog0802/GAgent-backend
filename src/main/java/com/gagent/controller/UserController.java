package com.gagent.controller;

import com.gagent.config.JwtUtil;
import com.gagent.dto.UserDto;
import com.gagent.entity.User;
import com.gagent.repository.UserRepository;
import com.gagent.service.OAuthTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final OAuthTokenService oauthTokenService;

    @GetMapping("/me")
    public ResponseEntity<UserDto> getMe(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid token");
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        String userIdStr = jwtUtil.getUserIdFromToken(token);
        Integer userId = Integer.parseInt(userIdStr);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return ResponseEntity.ok(toUserDto(user));
    }

    private UserDto toUserDto(User user) {
        boolean hasGoogleToken = user.getGoogleAccessToken() != null && !user.getGoogleAccessToken().isBlank();
        boolean hasGithubToken = user.getGithubAccessToken() != null && !user.getGithubAccessToken().isBlank();

        boolean googleExpired = hasGoogleToken && !oauthTokenService.isGoogleTokenValid(user);
        boolean githubExpired = hasGithubToken && !oauthTokenService.isGithubTokenValid(user);

        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .userName(user.getUserName())
                .isGoogleConnected(hasGoogleToken)
                .isGoogleLogin(user.getGoogleId() != null)
                .isGithubConnected(hasGithubToken)
                .isGoogleExpired(googleExpired)
                .isGithubExpired(githubExpired)
                .githubLogin(user.getGithubLogin())
                .build();
    }
}
