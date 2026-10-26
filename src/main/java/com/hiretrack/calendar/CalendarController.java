package com.hiretrack.calendar;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hiretrack.calendar.CalendarDtos.CalendarSubscription;

@RestController
public class CalendarController {

    private static final MediaType TEXT_CALENDAR = MediaType.parseMediaType("text/calendar;charset=UTF-8");

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    /** 캘린더 앱이 인증 헤더 없이 읽어 가는 구독 피드. URL 의 토큰이 곧 인증이다. */
    @GetMapping("/api/calendar/{token}.ics")
    public ResponseEntity<String> feed(@PathVariable String token) {
        return ResponseEntity.ok()
                .contentType(TEXT_CALENDAR)
                .header("Cache-Control", "private, max-age=300")
                .body(calendarService.feed(token));
    }

    @GetMapping("/api/users/me/calendar")
    public CalendarSubscription subscription(@AuthenticationPrincipal Long userId) {
        return calendarService.subscription(userId);
    }

    @PostMapping("/api/users/me/calendar/regenerate")
    public CalendarSubscription regenerate(@AuthenticationPrincipal Long userId) {
        return calendarService.regenerate(userId);
    }
}
