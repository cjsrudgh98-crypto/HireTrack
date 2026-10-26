package com.hiretrack.notification.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record NotificationSettingsRequest(
        @NotNull(message = "알림 사용 여부를 입력해 주세요.")
        Boolean emailEnabled,

        @NotNull(message = "알림 시점을 입력해 주세요.")
        List<@NotNull Integer> remindDays,

        @NotNull(message = "알림 시각을 입력해 주세요.")
        @Min(0) @Max(23)
        Integer sendHour
) {
}
