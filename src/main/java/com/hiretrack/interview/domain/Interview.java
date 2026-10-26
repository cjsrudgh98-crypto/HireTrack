package com.hiretrack.interview.domain;

import java.time.Instant;

import com.hiretrack.posting.domain.JobPosting;

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
@Table(name = "interviews", indexes = {
        @Index(name = "idx_interview_posting", columnList = "posting_id"),
        @Index(name = "idx_interview_starts_at", columnList = "starts_at")
})
public class Interview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "posting_id", nullable = false)
    private JobPosting posting;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewType type;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private int durationMinutes;

    @Column(length = 200)
    private String location;

    @Column(length = 2000)
    private String memo;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Interview() {
    }

    public Interview(JobPosting posting, InterviewType type, Instant startsAt, int durationMinutes,
                     String location, String memo, Instant now) {
        this.posting = posting;
        this.createdAt = now;
        update(type, startsAt, durationMinutes, location, memo, now);
    }

    public void update(InterviewType type, Instant startsAt, int durationMinutes, String location, String memo, Instant now) {
        this.type = type;
        this.startsAt = startsAt;
        this.durationMinutes = durationMinutes;
        this.location = location;
        this.memo = memo;
        this.updatedAt = now;
    }

    public boolean isOwnedBy(Long userId) {
        return posting.isOwnedBy(userId);
    }

    public Instant getEndsAt() {
        return startsAt.plusSeconds(durationMinutes * 60L);
    }

    public Long getId() {
        return id;
    }

    public JobPosting getPosting() {
        return posting;
    }

    public InterviewType getType() {
        return type;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getLocation() {
        return location;
    }

    public String getMemo() {
        return memo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
