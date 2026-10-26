package com.hiretrack.parse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ParseRequest(
        @NotBlank(message = "공고 URL 을 입력해 주세요.")
        @Size(max = 2000)
        String url
) {
}
