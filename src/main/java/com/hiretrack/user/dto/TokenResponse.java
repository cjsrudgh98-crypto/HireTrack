package com.hiretrack.user.dto;

public record TokenResponse(String accessToken, String tokenType, UserResponse user) {

    public static TokenResponse bearer(String accessToken, UserResponse user) {
        return new TokenResponse(accessToken, "Bearer", user);
    }
}
