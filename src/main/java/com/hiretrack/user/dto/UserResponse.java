package com.hiretrack.user.dto;

import java.time.OffsetDateTime;

import com.hiretrack.common.config.TimeConfig;
import com.hiretrack.user.User;

public record UserResponse(Long id, String email, OffsetDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), TimeConfig.toOffset(user.getCreatedAt()));
    }
}
