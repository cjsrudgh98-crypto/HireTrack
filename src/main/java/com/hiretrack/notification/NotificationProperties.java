package com.hiretrack.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl  메일 본문의 "지원 현황 보러가기" 링크에 쓸 프론트엔드 주소
 * @param mailFrom 보내는 사람 주소
 */
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(String baseUrl, String mailFrom) {
}
