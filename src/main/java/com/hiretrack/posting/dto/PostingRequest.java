package com.hiretrack.posting.dto;

import java.time.LocalDate;

import com.hiretrack.posting.domain.EmploymentType;
import com.hiretrack.posting.domain.PostingContent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PostingRequest(
        @NotBlank(message = "회사명을 입력해 주세요.")
        @Size(max = 100)
        String company,

        @NotBlank(message = "직무명을 입력해 주세요.")
        @Size(max = 200)
        String title,

        @Size(max = 500)
        @Pattern(regexp = "^(https?://.*)?$", message = "http:// 또는 https:// 로 시작하는 주소를 입력해 주세요.")
        String url,

        @Size(max = 100)
        String location,

        EmploymentType employmentType,

        LocalDate deadline,

        @Size(max = 5000)
        String requirements,

        @Size(max = 10000)
        String memo
) {

    public PostingContent toContent() {
        return new PostingContent(company.trim(), title.trim(), blankToNull(url), blankToNull(location),
                employmentType, deadline, blankToNull(requirements));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
