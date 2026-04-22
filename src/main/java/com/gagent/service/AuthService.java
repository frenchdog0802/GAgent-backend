package com.gagent.service;

import com.gagent.config.JwtUtil;
import com.gagent.dto.*;
import com.gagent.entity.User;
import com.gagent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Authentication service — mirrors services/auth.service.js
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    // ── Signup ──

    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is taken");
        }

        String salt = makeSalt();
        String hashedPassword = encryptPassword(request.getPassword(), salt);

        User user = User.builder()
                .userName(request.getUserName())
                .email(request.getEmail())
                .salt(salt)
                .hashedPassword(hashedPassword)
                .build();

        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId());
        return SignupResponse.builder()
                .token(token)
                .user(toUserDto(user))
                .build();
    }

    // ── Signin ──

    public SigninResponse signin(SigninRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "User not found"));

        if (!authenticate(request.getPassword(), user.getSalt(), user.getHashedPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and password don't match.");
        }

        String token = jwtUtil.generateToken(user.getId());
        return SigninResponse.builder()
                .token(token)
                .user(toUserDto(user))
                .build();
    }

    // // ── Google Login ──

    // public GoogleLoginResponse googleLogin(GoogleLoginRequest request) {
    // try {
    // RestTemplate restTemplate = new RestTemplate();

    // // Step 1: Validate token
    // @SuppressWarnings("unchecked")
    // Map<String, Object> tokenInfo = restTemplate.getForObject(
    // "https://www.googleapis.com/oauth2/v3/tokeninfo?access_token=" +
    // request.getToken(),
    // Map.class);

    // if (tokenInfo == null)
    // throw new BadRequestException("Invalid Google token");

    // String googleId = (String) tokenInfo.get("sub");

    // // Check if user exists
    // Optional<User> existingUser = userRepository.findByGoogleId(googleId);
    // if (existingUser.isPresent()) {
    // User user = existingUser.get();
    // String token = jwtUtil.generateToken(user.getId());
    // return GoogleLoginResponse.builder()
    // .token(token)
    // .user(toUserDto(user))
    // .build();
    // }

    // // Step 2: Get user info
    // @SuppressWarnings("unchecked")
    // Map<String, Object> userInfo = restTemplate.getForObject(
    // "https://www.googleapis.com/oauth2/v3/userinfo?access_token=" +
    // request.getToken(),
    // Map.class);

    // if (userInfo == null)
    // throw new BadRequestException("Failed to fetch Google user info");

    // String salt = makeSalt();
    // User newUser = User.builder()
    // .googleId(googleId)
    // .email((String) userInfo.get("email"))
    // .firstName((String) userInfo.getOrDefault("given_name", "User"))
    // .lastName((String) userInfo.getOrDefault("family_name", ""))
    // .name((String) userInfo.getOrDefault("name", "User"))
    // .picture((String) userInfo.get("picture"))
    // .salt(salt)
    // .hashedPassword(encryptPassword(UUID.randomUUID().toString(), salt))
    // .connectAccount("Google")
    // .role("user")
    // .build();

    // newUser = userRepository.save(newUser);
    // String token = jwtUtil.generateToken(newUser.getId());
    // return GoogleLoginResponse.builder()
    // .token(token)
    // .user(toUserDto(newUser))
    // .build();
    // } catch (BadRequestException e) {
    // throw e;
    // } catch (Exception e) {
    // log.error("Google login error", e);
    // throw new BadRequestException("Authentication failed: Invalid or expired
    // Google Access Token.");
    // }
    // }

    // ── Helper: Convert User entity to UserDto ──

    private UserDto toUserDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .userName(user.getUserName())
                .build();
    }

    // ── Password Helpers (mirrors Node.js crypto HMAC SHA-1) ──

    private String makeSalt() {
        return String.valueOf(Math.round(System.currentTimeMillis() * Math.random()));
    }

    private String encryptPassword(String password, String salt) {
        if (password == null || password.isEmpty())
            return "";
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] bytes = mac.doFinal(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            log.error("Error encrypting password", e);
            return "";
        }
    }

    private boolean authenticate(String plainText, String salt, String hashedPassword) {
        return encryptPassword(plainText, salt).equals(hashedPassword);
    }
}
