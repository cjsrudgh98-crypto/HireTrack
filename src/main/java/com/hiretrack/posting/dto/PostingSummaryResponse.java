package com.hiretrack.posting.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.hiretrack.posting.domain.Application;
import com.hiretrack.posting.domain.ApplicationStatus;
import com.hiretrack.posting.domain.EmploymentType;
import com.hiretrack.posting.domain.JobPosting;

/** 칸반 카드용 목록 응답. */
public record PostingSummaryResponse(
        Long id,
        String company,
        String title,
        String location,
        EmploymentType employmentType,
        LocalDate deadline,
        ApplicationStatus status,
        OffsetDateTime appliedAt,
        OffsetDateTime createdAt
) {

    public static PostingSummaryResponse from(JobPosting posting) {
        Application application = posting.getApplication();
        return new PostingSummaryResponse(
                posting.getId(),
                posting.getCompany(),
                posting.getTitle(),
                posting.getLocation(),
                posting.getEmploymentType(),
                posting.getDeadline(),
                application.getStatus(),
                Times.toOffset(application.getAppliedAt()),
                Times.toOffset(posting.getCreatedAt()));
    }
}
