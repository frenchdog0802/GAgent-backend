package com.gagent.controller;

import com.gagent.dto.RunRequest;
import com.gagent.dto.RunResponse;
import com.gagent.service.GagentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class GagentController {

    private final GagentService gagentService;

    @PostMapping("/run")
    public ResponseEntity<RunResponse> runAgent(@RequestBody RunRequest request) {
        log.info("Received run request: {}", request.getMessage());
        RunResponse response = gagentService.processRequest(request);
        return ResponseEntity.ok(response);
    }
}
