package com.hiretrack.common.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param allowedOriginPatterns API 를 다른 출처에서 부를 수 있게 허용할 출처.
 *                              운영에서는 확장 프로그램 ID 를 정확히 적는다 (chrome-extension://abcd...).
 *                              웹앱은 같은 도메인(Nginx)에서 /api 를 부르므로 CORS 가 필요 없다.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOriginPatterns) {
}
