package com.hiretrack.parse;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.hiretrack.common.error.BusinessException;
import com.hiretrack.common.error.ErrorCode;
import com.hiretrack.common.security.SlidingWindowCounter;

/**
 * 사용자당 분당 추출 요청 수 제한(슬라이딩 윈도). 대상 사이트에 부담을 주지 않기 위한 장치.
 * 단일 서버 기준 메모리 구현이며, 서버를 여러 대 띄우면 Redis 등 공유 저장소로 옮겨야 한다.
 */
@Component
public class ParseRateLimiter {

    private final SlidingWindowCounter<Long> requests = new SlidingWindowCounter<>(Duration.ofMinutes(1));
    private final int limit;
    private final Clock clock;

    public ParseRateLimiter(ParseProperties properties, Clock clock) {
        this.limit = properties.rateLimitPerMinute();
        this.clock = clock;
    }

    public void acquire(Long userId) {
        if (!requests.tryAcquire(userId, limit, clock.instant())) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }
}
