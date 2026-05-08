package com.gagent.controller;

import com.gagent.dto.RunRequest;
import com.gagent.dto.RunResponse;
import com.gagent.service.GagentService;
import com.gagent.config.JwtUtil;
import com.gagent.entity.ActivityLog;
import com.gagent.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class GagentController {

    private final GagentService gagentService;
    private final ActivityLogRepository activityLogRepository;
    private final JwtUtil jwtUtil;

    @PostMapping("/run")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<RunResponse> runAgent(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody RunRequest request) {
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }
        
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }

        String userId = jwtUtil.getUserIdFromToken(token);

        log.info("Received run request from user {}: {}", userId, request.getMessage());
        RunResponse response = gagentService.processRequest(request, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/logs")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Iterable<ActivityLog>> getLogs(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }
        
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }

        String userId = jwtUtil.getUserIdFromToken(token);
        
        Iterable<ActivityLog> logs = activityLogRepository.findByUserIdOrderByTimestampDesc(userId);
        return ResponseEntity.ok(logs);
    }
}
