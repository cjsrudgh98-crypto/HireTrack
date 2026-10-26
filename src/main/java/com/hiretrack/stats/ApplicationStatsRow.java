package com.hiretrack.stats;

import java.time.Instant;

import com.hiretrack.posting.domain.ApplicationStatus;
import com.hiretrack.posting.domain.EmploymentType;

/**
 * 지원 1건의 집계용 요약 (JPQL 생성자 표현식으로 바로 만든다).
 *
 * @param maxStage  되돌리지 않은 이력 중 도달한 가장 높은 단계 (0 관심, 1 지원, 2 서류합격, 3 면접, 4 합격)
 * @param appliedOn 처음 지원 단계 이상에 도달한 시각 — 단계를 건너뛴 경우도 포함 (없으면 null)
 */
public record ApplicationStatsRow(
        Long applicationId,
        ApplicationStatus currentStatus,
        EmploymentType employmentType,
        String location,
        Integer maxStage,
        Instant appliedOn
) {

    public boolean applied() {
        return maxStage != null && maxStage >= 1;
    }

    public boolean reached(int stage) {
        return maxStage != null && maxStage >= stage;
    }

    public boolean rejected() {
        return currentStatus == ApplicationStatus.REJECTED;
    }
}
