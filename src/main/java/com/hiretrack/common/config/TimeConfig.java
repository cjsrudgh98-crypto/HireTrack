package com.hiretrack.common.config;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** 응답 시각은 모두 한국 시간(+09:00)으로 내려준다. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** 응답용 시각 변환. 초 단위로 잘라 "2026-10-26T16:00:00+09:00" 형태로 내려준다. */
    public static OffsetDateTime toOffset(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS).atZone(ZONE).toOffsetDateTime();
    }

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
