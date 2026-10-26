package com.hiretrack.notification;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 사용자별 알림 설정. 행이 없으면 {@link #defaults(Long)} 값으로 동작한다. */
@Entity
@Table(name = "notification_settings")
public class NotificationSettings {

    public static final List<Integer> ALLOWED_DAYS = List.of(7, 3, 1);
    public static final List<Integer> DEFAULT_DAYS = List.of(3, 1);
    public static final int DEFAULT_SEND_HOUR = 9;

    @Id
    private Long userId;

    @Column(nullable = false)
    private boolean emailEnabled;

    /** 예: "3,1" */
    @Column(nullable = false, length = 20)
    private String remindDays;

    @Column(nullable = false)
    private int sendHour;

    protected NotificationSettings() {
    }

    public NotificationSettings(Long userId, boolean emailEnabled, List<Integer> remindDays, int sendHour) {
        this.userId = userId;
        update(emailEnabled, remindDays, sendHour);
    }

    public static NotificationSettings defaults(Long userId) {
        return new NotificationSettings(userId, true, DEFAULT_DAYS, DEFAULT_SEND_HOUR);
    }

    public void update(boolean emailEnabled, List<Integer> remindDays, int sendHour) {
        this.emailEnabled = emailEnabled;
        this.remindDays = remindDays.stream().distinct().sorted((a, b) -> b - a)
                .map(String::valueOf).collect(Collectors.joining(","));
        this.sendHour = sendHour;
    }

    public List<Integer> getRemindDayList() {
        if (remindDays == null || remindDays.isBlank()) return List.of();
        return Arrays.stream(remindDays.split(",")).map(String::trim).map(Integer::valueOf).toList();
    }

    /** 지금 시각(서울 기준 시)에 이 사용자에게 보낼 차례인지 */
    public boolean shouldSendAt(int hour) {
        return emailEnabled && sendHour == hour;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public int getSendHour() {
        return sendHour;
    }
}
