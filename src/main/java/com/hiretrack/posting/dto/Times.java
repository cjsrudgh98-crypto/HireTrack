package com.hiretrack.posting.dto;

import java.time.Instant;
import java.time.OffsetDateTime;

import com.hiretrack.common.config.TimeConfig;

final class Times {

    private Times() {
    }

    static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : TimeConfig.toOffset(instant);
    }
}
