package com.hiretrack.calendar;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 사용자별 캘린더 구독 토큰. 캘린더 앱은 인증 헤더를 보낼 수 없어서 URL 자체가 비밀키 역할을 한다.
 * 구독 URL 을 설정 화면에서 언제든 다시 복사할 수 있어야 해서 토큰 원문을 저장한다(읽기 전용 피드 접근만 가능).
 * 유출되면 재발급으로 이전 URL 을 즉시 무효화한다.
 */
@Entity
@Table(name = "calendar_feeds")
public class CalendarFeed {

    @Id
    private Long userId;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(nullable = false)
    private Instant createdAt;

    protected CalendarFeed() {
    }

    public CalendarFeed(Long userId, String token, Instant createdAt) {
        this.userId = userId;
        this.token = token;
        this.createdAt = createdAt;
    }

    public void rotate(String token, Instant now) {
        this.token = token;
        this.createdAt = now;
    }

    public Long getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
