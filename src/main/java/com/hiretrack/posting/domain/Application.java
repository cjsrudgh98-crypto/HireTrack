package com.hiretrack.posting.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** 공고에 대한 지원 레코드. 상태·지원일·메모와 상태 변경 이력을 가진다. */
@Entity
@Table(name = "application")
public class Application {

    /** 이 시간 안에 직전 변경을 그대로 되돌리면 실수로 본다. */
    public static final Duration REVERT_WINDOW = Duration.ofMinutes(10);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "posting_id", nullable = false, unique = true)
    private JobPosting posting;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    private Instant appliedAt;

    @Column(length = 10000)
    private String memo;

    // 되돌림 판단이 "마지막 이력"에 의존하므로 순서를 명시한다. PostgreSQL 은 ORDER BY 가 없으면 UPDATE(reverted 표시) 뒤 순서가 바뀔 수 있다
    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt asc, id asc")
    private List<StatusHistory> histories = new ArrayList<>();

    protected Application() {
    }

    Application(JobPosting posting, String memo, Instant now) {
        this.posting = posting;
        this.status = ApplicationStatus.INTERESTED;
        this.memo = memo;
        this.histories.add(new StatusHistory(this, null, ApplicationStatus.INTERESTED, now));
    }

    /**
     * 상태를 바꾸고 이력을 남긴다. 같은 상태로의 변경은 무시한다.
     * <ul>
     *   <li>APPLIED 로 처음 바뀔 때만 지원일을 기록한다.</li>
     *   <li>직전 변경을 {@link #REVERT_WINDOW} 안에 그대로 되돌리면(A→B 직후 B→A) 실수로 보고
     *       두 이력 모두 reverted 로 표시한다. 이력은 남기되 통계에서는 빠진다.
     *       되돌린 변경이 첫 지원이었다면 지원일도 지운다.</li>
     * </ul>
     */
    public StatusChange changeStatus(ApplicationStatus next, Instant now) {
        if (status == next) {
            return StatusChange.NONE;
        }
        StatusHistory last = lastEffectiveHistory();
        boolean revert = last != null
                && last.getFromStatus() == next
                && last.getToStatus() == status
                && !Duration.between(last.getChangedAt(), now).minus(REVERT_WINDOW).isPositive();

        StatusHistory history = new StatusHistory(this, status, next, now);
        histories.add(history);
        this.status = next;
        posting.touch(now);

        if (revert) {
            last.markReverted();
            history.markReverted();
            boolean undoFirstApply = last.getToStatus() == ApplicationStatus.APPLIED
                    && last.getChangedAt().equals(appliedAt);
            if (undoFirstApply) this.appliedAt = null;
            return new StatusChange(true, false, undoFirstApply);
        }
        if (next == ApplicationStatus.APPLIED && appliedAt == null) {
            this.appliedAt = now;
            return new StatusChange(true, true, false);
        }
        return new StatusChange(true, false, false);
    }

    private StatusHistory lastEffectiveHistory() {
        for (int i = histories.size() - 1; i >= 0; i--) {
            if (!histories.get(i).isReverted()) return histories.get(i);
        }
        return null;
    }

    public void updateMemo(String memo, Instant now) {
        this.memo = memo;
        posting.touch(now);
    }

    public Long getId() {
        return id;
    }

    public JobPosting getPosting() {
        return posting;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public String getMemo() {
        return memo;
    }

    public List<StatusHistory> getHistories() {
        return List.copyOf(histories);
    }
}
