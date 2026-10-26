package com.hiretrack.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.hiretrack.common.error.BusinessException;
import com.hiretrack.common.error.ErrorCode;
import com.hiretrack.common.security.SlidingWindowCounter;

/**
 * 비밀번호 대입 방어.
 * <ul>
 *   <li>IP 당 분당 요청 수 제한 — 여러 계정을 돌아가며 시도하는 경우</li>
 *   <li>이메일당 실패 횟수 제한 — 한 계정을 노리는 경우. 성공하면 초기화.
 *       없는 이메일도 똑같이 세서, 잠금 여부로 가입 여부를 알 수 없게 한다.</li>
 * </ul>
 */
@Component
public class LoginThrottle {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottle.class);

    private final LoginProperties properties;
    private final Clock clock;
    private final SlidingWindowCounter<String> ipRequests = new SlidingWindowCounter<>(Duration.ofMinutes(1));
    private final SlidingWindowCounter<String> failures;

    public LoginThrottle(LoginProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.failures = new SlidingWindowCounter<>(properties.lockDuration());
    }

    /** 로그인·가입 요청마다 먼저 호출 */
    public void checkIp(String ip) {
        if (!ipRequests.tryAcquire(ip, properties.ipRequestsPerMinute(), clock.instant())) {
            log.warn("auth-rate-limited ip={}", ip);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }

    /** 비밀번호를 확인하기 전에 호출 — 잠긴 이메일이면 비밀번호가 맞아도 거절 */
    public void checkNotLocked(String email) {
        if (failures.count(email, clock.instant()) >= properties.maxFailures()) {
            throw new BusinessException(ErrorCode.LOGIN_LOCKED);
        }
    }

    public void onFailure(String email) {
        Instant now = clock.instant();
        failures.record(email, now);
        if (failures.count(email, now) == properties.maxFailures()) {
            log.warn("login-locked email-hash={}", Integer.toHexString(email.hashCode()));
        }
    }

    public void onSuccess(String email) {
        failures.reset(email);
    }
}
