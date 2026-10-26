package com.hiretrack.notification;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 발송 기록. (posting_id, remind_day) 유니크 제약 덕분에 스케줄러가 두 번 돌거나
 * 여러 서버에서 동시에 돌아도 같은 알림은 한 번만 기록·발송된다.
 */
@Entity
@Table(name = "notification_logs",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_posting_day", columnNames = {"posting_id", "remind_day"}))
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "posting_id", nullable = false)
    private Long postingId;

    @Column(name = "remind_day", nullable = false)
    private int remindDay;

    @Column(nullable = false)
    private Instant sentAt;

    protected NotificationLog() {
    }

    public NotificationLog(Long userId, Long postingId, int remindDay, Instant sentAt) {
        this.userId = userId;
        this.postingId = postingId;
        this.remindDay = remindDay;
        this.sentAt = sentAt;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getPostingId() {
        return postingId;
    }

    public int getRemindDay() {
        return remindDay;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
