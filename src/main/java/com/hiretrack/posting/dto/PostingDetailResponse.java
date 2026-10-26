package com.hiretrack.posting.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.hiretrack.posting.domain.Application;
import com.hiretrack.posting.domain.ApplicationStatus;
import com.hiretrack.posting.domain.EmploymentType;
import com.hiretrack.posting.domain.JobPosting;

public record PostingDetailResponse(
        Long id,
        String company,
        String title,
        String url,
        String location,
        EmploymentType employmentType,
        LocalDate deadline,
        String requirements,
        String memo,
        ApplicationStatus status,
        OffsetDateTime appliedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static PostingDetailResponse from(JobPosting posting) {
        Application application = posting.getApplication();
        return new PostingDetailResponse(
                posting.getId(),
                posting.getCompany(),
                posting.getTitle(),
                posting.getUrl(),
                posting.getLocation(),
                posting.getEmploymentType(),
                posting.getDeadline(),
                posting.getRequirements(),
                application.getMemo(),
                application.getStatus(),
                Times.toOffset(application.getAppliedAt()),
                Times.toOffset(posting.getCreatedAt()),
                Times.toOffset(posting.getUpdatedAt()));
    }
}
