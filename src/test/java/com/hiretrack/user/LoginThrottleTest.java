package com.hiretrack.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.hiretrack.common.error.BusinessException;
import com.hiretrack.common.error.ErrorCode;

class LoginThrottleTest {

    /** 테스트 안에서 시간을 앞으로 돌릴 수 있는 시계 */
    static class MovableClock extends Clock {
        Instant now = Instant.parse("2026-10-26T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovableClock clock = new MovableClock();
    private final LoginThrottle throttle = new LoginThrottle(new LoginProperties(5, Duration.ofMinutes(15), 3), clock);

    @Test
    void 이메일당_5번_실패하면_잠기고_15분이_지나면_풀린다() {
        for (int i = 0; i < 4; i++) throttle.onFailure("a@test.com");
        assertThatCode(() -> throttle.checkNotLocked("a@test.com")).doesNotThrowAnyException();

        throttle.onFailure("a@test.com");
        assertLocked("a@test.com");
        assertThatCode(() -> throttle.checkNotLocked("b@test.com")).doesNotThrowAnyException(); // 다른 계정은 영향 없음

        clock.advance(Duration.ofMinutes(15));
        assertThatCode(() -> throttle.checkNotLocked("a@test.com")).doesNotThrowAnyException();
    }

    @Test
    void 성공하면_실패_횟수가_초기화된다() {
        for (int i = 0; i < 4; i++) throttle.onFailure("a@test.com");
        throttle.onSuccess("a@test.com");
        throttle.onFailure("a@test.com");

        assertThatCode(() -> throttle.checkNotLocked("a@test.com")).doesNotThrowAnyException();
    }

    @Test
    void IP당_분당_요청_수를_넘으면_429_1분_뒤_다시_허용() {
        for (int i = 0; i < 3; i++) throttle.checkIp("1.2.3.4");

        assertThatThrownBy(() -> throttle.checkIp("1.2.3.4"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
        assertThatCode(() -> throttle.checkIp("5.6.7.8")).doesNotThrowAnyException();

        clock.advance(Duration.ofMinutes(1));
        assertThatCode(() -> throttle.checkIp("1.2.3.4")).doesNotThrowAnyException();
    }

    private void assertLocked(String email) {
        assertThatThrownBy(() -> throttle.checkNotLocked(email))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_LOCKED);
        assertThat(ErrorCode.LOGIN_LOCKED.getStatus().value()).isEqualTo(429);
    }
}
