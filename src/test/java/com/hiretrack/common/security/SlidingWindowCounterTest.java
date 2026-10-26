package com.hiretrack.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class SlidingWindowCounterTest {

    private static final Instant T0 = Instant.parse("2026-10-26T00:00:00Z");

    @Test
    void 기간_안의_요청만_센다() {
        SlidingWindowCounter<String> counter = new SlidingWindowCounter<>(Duration.ofMinutes(1));

        assertThat(counter.tryAcquire("k", 2, T0)).isTrue();
        assertThat(counter.tryAcquire("k", 2, T0.plusSeconds(10))).isTrue();
        assertThat(counter.tryAcquire("k", 2, T0.plusSeconds(20))).isFalse();
        // 첫 요청이 1분을 벗어나면 한 자리가 빈다
        assertThat(counter.tryAcquire("k", 2, T0.plusSeconds(60))).isTrue();
        assertThat(counter.count("k", T0.plusSeconds(60))).isEqualTo(2);
    }

    @Test
    void 기록이_모두_지난_키는_지워서_메모리가_늘지_않는다() {
        SlidingWindowCounter<String> counter = new SlidingWindowCounter<>(Duration.ofMinutes(1));
        for (int i = 0; i < 100; i++) counter.record("ip-" + i, T0);
        assertThat(counter.trackedKeys()).isEqualTo(100);

        for (int i = 0; i < 100; i++) counter.count("ip-" + i, T0.plusSeconds(61));

        assertThat(counter.trackedKeys()).isZero();
    }
}
