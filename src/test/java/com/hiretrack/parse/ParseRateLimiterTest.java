package com.hiretrack.parse;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import com.hiretrack.common.error.BusinessException;

class ParseRateLimiterTest {

    private Instant now = Instant.parse("2026-10-26T00:00:00Z");

    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };

    private final ParseRateLimiter limiter = new ParseRateLimiter(
            new ParseProperties(Duration.ofSeconds(5), DataSize.ofMegabytes(2), 3), clock);

    @Test
    void 분당_한도를_넘으면_거부하고_1분이_지나면_다시_허용() {
        for (int i = 0; i < 3; i++) limiter.acquire(1L);
        assertThatThrownBy(() -> limiter.acquire(1L)).isInstanceOf(BusinessException.class);

        assertThatCode(() -> limiter.acquire(2L)).doesNotThrowAnyException(); // 다른 사용자는 별도

        now = now.plusSeconds(61);
        assertThatCode(() -> limiter.acquire(1L)).doesNotThrowAnyException();
    }
}
