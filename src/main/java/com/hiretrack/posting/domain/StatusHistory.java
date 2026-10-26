package com.hiretrack.posting.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "status_history",
        indexes = @Index(name = "idx_history_application_status", columnList = "application_id, to_status"))
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    /** 최초 등록 이력은 이전 상태가 없으므로 null. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus toStatus;

    @Column(nullable = false)
    private Instant changedAt;

    /** 실수로 보고 되돌린 변경. 타임라인에는 보이지만 통계에서는 제외한다. */
    @Column(nullable = false)
    private boolean reverted;

    protected StatusHistory() {
    }

    StatusHistory(Application application, ApplicationStatus fromStatus, ApplicationStatus toStatus, Instant changedAt) {
        this.application = application;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
    }

    public Long getId() {
        return id;
    }

    public ApplicationStatus getFromStatus() {
        return fromStatus;
    }

    public ApplicationStatus getToStatus() {
        return toStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public boolean isReverted() {
        return reverted;
    }

    void markReverted() {
        this.reverted = true;
    }
}
