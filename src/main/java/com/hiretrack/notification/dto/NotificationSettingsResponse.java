package com.hiretrack.notification.dto;

import java.util.List;

import com.hiretrack.notification.NotificationSettings;

/**
 * @param email 알림을 받을 주소(가입 이메일, 읽기 전용)
 */
public record NotificationSettingsResponse(
        boolean emailEnabled,
        List<Integer> remindDays,
        int sendHour,
        String email
) {

    public static NotificationSettingsResponse of(NotificationSettings settings, String email) {
        return new NotificationSettingsResponse(settings.isEmailEnabled(), settings.getRemindDayList(),
                settings.getSendHour(), email);
    }
}
