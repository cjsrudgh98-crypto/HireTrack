package com.hiretrack.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hiretrack.user.dto.AuthRequest;
import com.hiretrack.user.dto.TokenResponse;
import com.hiretrack.user.dto.UserResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse signup(@Valid @RequestBody AuthRequest request, HttpServletRequest http) {
        return authService.signup(request, http.getRemoteAddr());
    }

    @PostMapping("/api/auth/login")
    public TokenResponse login(@Valid @RequestBody AuthRequest request, HttpServletRequest http) {
        return authService.login(request, http.getRemoteAddr());
    }

    @PostMapping("/api/auth/extension/login")
    public TokenResponse extensionLogin(@Valid @RequestBody AuthRequest request, HttpServletRequest http) {
        return authService.loginForExtension(request, http.getRemoteAddr());
    }

    @GetMapping("/api/users/me")
    public UserResponse me(@AuthenticationPrincipal Long userId) {
        return authService.me(userId);
    }
}
