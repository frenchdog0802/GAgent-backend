package com.gagent.controller;

import com.gagent.service.AuthService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import com.gagent.dto.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse data = authService.signup(request);
        return ResponseEntity.ok(data);
    }

    @PostMapping("/signin")
    public ResponseEntity<SigninResponse> signin(@Valid @RequestBody SigninRequest request) {
        SigninResponse data = authService.signin(request);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/signout")
    public ResponseEntity<SignoutResponse> signout() {
        return ResponseEntity.ok(new SignoutResponse("signed out"));
    }

    // @PostMapping("/google-login")
    // public ResponseEntity<GoogleLoginResponse> googleLogin(@Valid @RequestBody
    // GoogleLoginRequest request) {
    // GoogleLoginResponse data = authService.googleLogin(request);
    // return ResponseEntity.ok(data);
    // }
}
