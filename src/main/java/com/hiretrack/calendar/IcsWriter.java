package com.hiretrack.calendar;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 최소한의 iCalendar(RFC 5545) 작성기. 이 앱에 필요한 VEVENT/VALARM 만 다룬다.
 * <ul>
 *   <li>줄 끝은 CRLF</li>
 *   <li>한 줄이 75 옥텟을 넘으면 접는다(다음 줄은 공백으로 시작). 한글은 UTF-8 3바이트라 글자 수가 아니라 바이트로 센다</li>
 *   <li>TEXT 값의 \ ; , 줄바꿈은 이스케이프</li>
 *   <li>시각은 UTC(Z)로 써서 VTIMEZONE 정의 없이도 모든 캘린더 앱에서 같은 시각으로 보이게 한다</li>
 * </ul>
 */
public class IcsWriter {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MAX_OCTETS = 75;

    private final StringBuilder out = new StringBuilder();

    public IcsWriter beginCalendar(String name) {
        line("BEGIN:VCALENDAR");
        line("VERSION:2.0");
        line("PRODID:-//HireTrack//Job Application Tracker//KO");
        line("CALSCALE:GREGORIAN");
        line("METHOD:PUBLISH");
        property("X-WR-CALNAME", name);
        line("X-WR-TIMEZONE:Asia/Seoul");
        // 캘린더 앱에 새로 읽어 갈 주기를 제안 (실제 주기는 앱이 정한다 — 구글은 수 시간 이상)
        line("REFRESH-INTERVAL;VALUE=DURATION:PT1H");
        line("X-PUBLISHED-TTL:PT1H");
        return this;
    }

    public IcsWriter endCalendar() {
        line("END:VCALENDAR");
        return this;
    }

    /** 종일 일정 (마감일) */
    public IcsWriter allDayEvent(String uid, LocalDate date, String summary, String description, Instant stamp, Instant lastModified) {
        line("BEGIN:VEVENT");
        line("UID:" + uid);
        line("DTSTAMP:" + UTC.format(stamp));
        line("LAST-MODIFIED:" + UTC.format(lastModified));
        line("DTSTART;VALUE=DATE:" + DATE.format(date));
        line("DTEND;VALUE=DATE:" + DATE.format(date.plusDays(1)));
        property("SUMMARY", summary);
        if (description != null) property("DESCRIPTION", description);
        line("TRANSP:TRANSPARENT"); // 마감일은 "바쁨"으로 표시하지 않는다
        line("END:VEVENT");
        return this;
    }

    /** 시간 일정 (면접) + alarmMinutesBefore 분 전 알림 */
    public IcsWriter timedEvent(String uid, Instant start, Instant end, String summary, String location,
                                String description, int alarmMinutesBefore, Instant stamp, Instant lastModified) {
        line("BEGIN:VEVENT");
        line("UID:" + uid);
        line("DTSTAMP:" + UTC.format(stamp));
        line("LAST-MODIFIED:" + UTC.format(lastModified));
        line("DTSTART:" + UTC.format(start));
        line("DTEND:" + UTC.format(end));
        property("SUMMARY", summary);
        if (location != null) property("LOCATION", location);
        if (description != null) property("DESCRIPTION", description);
        line("BEGIN:VALARM");
        line("ACTION:DISPLAY");
        property("DESCRIPTION", summary);
        line("TRIGGER:-PT" + alarmMinutesBefore + "M");
        line("END:VALARM");
        line("END:VEVENT");
        return this;
    }

    @Override
    public String toString() {
        return out.toString();
    }

    private void property(String name, String text) {
        line(name + ":" + escape(text));
    }

    static String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    /** 75 옥텟 단위로 접는다. 멀티바이트 문자는 중간에서 자르지 않는다. */
    private void line(String content) {
        int octets = 0;
        int limit = MAX_OCTETS;
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            int size = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > limit) {
                out.append("\r\n ");
                octets = 0;
                limit = MAX_OCTETS - 1; // 접힌 줄은 맨 앞 공백 1옥텟 포함
            }
            out.appendCodePoint(cp);
            octets += size;
            i += Character.charCount(cp);
        }
        out.append("\r\n");
    }
}
