package com.hiretrack.parse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.hiretrack.common.error.BusinessException;
import com.hiretrack.common.error.ErrorCode;

/**
 * 사용자당 분당 추출 요청 수 제한(슬라이딩 윈도). 대상 사이트에 부담을 주지 않기 위한 장치.
 * 단일 서버 기준 메모리 구현이며, 서버를 여러 대 띄우면 Redis 등 공유 저장소로 옮겨야 한다.
 */
@Component
public class ParseRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<Long, Deque<Instant>> requests = new ConcurrentHashMap<>();
    private final int limit;
    private final Clock clock;

    public ParseRateLimiter(ParseProperties properties, Clock clock) {
        this.limit = properties.rateLimitPerMinute();
        this.clock = clock;
    }

    public void acquire(Long userId) {
        Instant now = clock.instant();
        Deque<Instant> window = requests.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && !window.peekFirst().isAfter(now.minus(WINDOW))) {
                window.pollFirst();
            }
            if (window.size() >= limit) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
            }
            window.addLast(now);
        }
    }
}
