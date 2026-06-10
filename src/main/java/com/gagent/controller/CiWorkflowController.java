package com.gagent.controller;

import com.gagent.config.JwtUtil;
import com.gagent.dto.CiWorkflowDto;
import com.gagent.entity.CiWorkflow;
import com.gagent.repository.CiWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/ci/workflows")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CiWorkflowController {

    private final CiWorkflowRepository ciWorkflowRepository;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<List<CiWorkflowDto>> listWorkflows(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Integer userId = getUserIdFromHeader(authHeader);
        List<CiWorkflowDto> workflows = ciWorkflowRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(workflows);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CiWorkflowDto> getWorkflow(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long id) {
        Integer userId = getUserIdFromHeader(authHeader);
        CiWorkflow workflow = ciWorkflowRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found"));

        if (!workflow.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return ResponseEntity.ok(toDto(workflow));
    }

    private CiWorkflowDto toDto(CiWorkflow workflow) {
        return CiWorkflowDto.builder()
                .id(workflow.getId())
                .webhookEventId(workflow.getWebhookEventId())
                .repository(workflow.getRepository())
                .status(workflow.getStatus())
                .prUrl(workflow.getPrUrl())
                .fixBranch(workflow.getFixBranch())
                .lastError(workflow.getLastError())
                .fixAttempt(workflow.getFixAttempt())
                .createdAt(workflow.getCreatedAt())
                .updatedAt(workflow.getUpdatedAt())
                .build();
    }

    private Integer getUserIdFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }

        return Integer.parseInt(jwtUtil.getUserIdFromToken(token));
    }
}
