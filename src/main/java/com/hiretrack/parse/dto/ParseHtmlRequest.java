package com.hiretrack.parse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 확장 프로그램이 사용자의 브라우저에서 이미 렌더링된 화면의 HTML 을 보낸다.
 * 서버는 이 URL 로 요청을 보내지 않는다(호스트로 파서를 고르는 데만 씀) — SSRF 와 무관.
 */
public record ParseHtmlRequest(
        @NotBlank @Size(max = 2000) String url,
        @NotBlank @Size(max = ParseHtmlRequest.MAX_HTML_CHARS, message = "페이지가 너무 큽니다(최대 2MB).") String html
) {
    public static final int MAX_HTML_CHARS = 2 * 1024 * 1024;
}
