package com.paynest.controller;

import com.paynest.dto.request.AuthLoginRequest;
import com.paynest.dto.response.AuthLoginResponse;
import com.paynest.dto.response.ChallengeTokenResponse;
import com.paynest.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthLoginResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/challenge-token")
    public ResponseEntity<ChallengeTokenResponse> generateChallengeToken(
            @RequestHeader("Authorization") String authorizationHeader,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(authService.generateChallengeToken(authorizationHeader, request.getRemoteAddr()));
    }
}
