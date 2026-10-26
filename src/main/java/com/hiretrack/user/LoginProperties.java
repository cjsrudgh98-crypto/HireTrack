package com.hiretrack.user;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param maxFailures         이메일당 이 횟수만큼 연속 실패하면 잠근다
 * @param lockDuration        실패를 세는 기간이자 잠금 시간
 * @param ipRequestsPerMinute IP 당 분당 로그인·가입 요청 수 (여러 계정에 비밀번호를 대입하는 시도 차단)
 */
@ConfigurationProperties(prefix = "auth.login")
public record LoginProperties(int maxFailures, Duration lockDuration, int ipRequestsPerMinute) {
}
