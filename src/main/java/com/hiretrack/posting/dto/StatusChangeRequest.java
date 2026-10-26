package com.hiretrack.posting.dto;

import com.hiretrack.posting.domain.ApplicationStatus;

import jakarta.validation.constraints.NotNull;

public record StatusChangeRequest(@NotNull(message = "변경할 상태를 입력해 주세요.") ApplicationStatus status) {
}
