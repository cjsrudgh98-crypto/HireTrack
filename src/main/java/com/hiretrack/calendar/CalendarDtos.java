package com.hiretrack.calendar;

import java.time.OffsetDateTime;

public final class CalendarDtos {

    private CalendarDtos() {
    }

    /** @param url 캘린더 앱에 붙여넣을 구독 주소 (비밀키 포함) */
    public record CalendarSubscription(String url, OffsetDateTime issuedAt) {
    }
}
