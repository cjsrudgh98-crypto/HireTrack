package com.hiretrack.posting.domain;

import java.time.LocalDate;

/** 사용자가 입력하는 공고 정보 묶음. */
public record PostingContent(
        String company,
        String title,
        String url,
        String location,
        EmploymentType employmentType,
        LocalDate deadline,
        String requirements
) {
}
