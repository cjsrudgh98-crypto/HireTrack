package com.hiretrack.common.security;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import com.hiretrack.common.error.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HTML 을 통째로 받는 엔드포인트의 본문 크기 상한. JSON 을 파싱해 메모리에 올리기 전에 Content-Length 로 거른다.
 * (본문 안의 html 필드 자체는 DTO 에서 한 번 더 2MB 로 검증)
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final String path;
    private final long maxBytes;
    private final JsonErrorWriter errorWriter;

    public RequestSizeLimitFilter(String path, long maxBytes, JsonErrorWriter errorWriter) {
        this.path = path;
        this.maxBytes = maxBytes;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !path.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            errorWriter.write(response, ErrorCode.PAYLOAD_TOO_LARGE);
            return;
        }
        chain.doFilter(request, response);
    }
}
