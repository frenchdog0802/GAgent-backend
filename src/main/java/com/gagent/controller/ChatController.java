package com.gagent.controller;

import com.gagent.config.JwtUtil;
import com.gagent.dto.ChatMessageDto;
import com.gagent.dto.ChatSessionDto;
import com.gagent.service.ChatService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;
    private final JwtUtil jwtUtil;

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<ChatSessionDto>> getSessions(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = validateAndGetUserId(authHeader);
        List<ChatSessionDto> sessions = chatService.getUserSessions(userId);
        return ResponseEntity.ok(sessions);
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ChatSessionDto> createSession(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = validateAndGetUserId(authHeader);
        ChatSessionDto session = chatService.createSession(userId);
        return ResponseEntity.ok(session);
    }

    @GetMapping("/{id}/messages")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<ChatMessageDto>> getMessages(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("id") Long sessionId) {
        validateAndGetUserId(authHeader);
        List<ChatMessageDto> messages = chatService.getSessionMessages(sessionId);
        return ResponseEntity.ok(messages);
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> deleteSession(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("id") Long sessionId) {
        validateAndGetUserId(authHeader);
        chatService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    private String validateAndGetUserId(String authHeader) {
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
