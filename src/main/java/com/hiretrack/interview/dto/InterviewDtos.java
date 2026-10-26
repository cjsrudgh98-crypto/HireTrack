package com.hiretrack.interview.dto;

import java.time.OffsetDateTime;

import com.hiretrack.common.config.TimeConfig;
import com.hiretrack.interview.domain.Interview;
import com.hiretrack.interview.domain.InterviewType;
import com.hiretrack.posting.domain.ApplicationStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class InterviewDtos {

    private InterviewDtos() {
    }

    /** startsAt 은 오프셋 포함 ISO 시각 (예: 2026-11-03T14:00:00+09:00) */
    public record InterviewRequest(
            @NotNull(message = "면접 유형을 골라 주세요.") InterviewType type,
            @NotNull(message = "면접 시각을 입력해 주세요.") OffsetDateTime startsAt,
            @Min(10) @Max(600) Integer durationMinutes,
            @Size(max = 200) String location,
            @Size(max = 2000) String memo
    ) {
        public int durationOrDefault() {
            return durationMinutes == null ? 60 : durationMinutes;
        }
    }

    /**
     * @param postingStatus 등록 후 프론트가 "상태를 면접 진행으로 바꿀까요?"를 물을지 판단하는 데 쓴다
     */
    public record InterviewResponse(
            Long id, Long postingId, String company, String postingTitle, ApplicationStatus postingStatus,
            InterviewType type, OffsetDateTime startsAt, OffsetDateTime endsAt, int durationMinutes,
            String location, String memo
    ) {
        public static InterviewResponse from(Interview i) {
            return new InterviewResponse(i.getId(), i.getPosting().getId(), i.getPosting().getCompany(),
                    i.getPosting().getTitle(), i.getPosting().getApplication().getStatus(), i.getType(),
                    TimeConfig.toOffset(i.getStartsAt()), TimeConfig.toOffset(i.getEndsAt()), i.getDurationMinutes(),
                    i.getLocation(), i.getMemo());
        }
    }
}
