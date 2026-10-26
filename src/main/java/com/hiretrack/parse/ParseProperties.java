package com.hiretrack.parse;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * @param timeout            페이지 요청 제한 시간 (연결+읽기)
 * @param maxBodySize        응답 본문 상한
 * @param rateLimitPerMinute 사용자당 분당 추출 요청 수
 */
@ConfigurationProperties(prefix = "parse")
public record ParseProperties(Duration timeout, DataSize maxBodySize, int rateLimitPerMinute) {
}
