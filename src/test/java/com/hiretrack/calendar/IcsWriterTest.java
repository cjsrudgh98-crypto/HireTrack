package com.hiretrack.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class IcsWriterTest {

    private static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");

    @Test
    void 특수문자를_이스케이프한다() {
        assertThat(IcsWriter.escape("a,b;c\\d\n둘째 줄")).isEqualTo("a\\,b\\;c\\\\d\\n둘째 줄");
    }

    @Test
    void 긴_한글_줄은_75옥텟_이내로_접고_펼치면_원문이_된다() {
        String summary = "[1차 면접] " + "가나다라마바사아자차카타파하".repeat(5);
        String ics = new IcsWriter().beginCalendar("테스트")
                .timedEvent("interview-1@hiretrack", NOW, NOW.plusSeconds(3600), summary, null, null, 60, NOW, NOW)
                .endCalendar().toString();

        assertThat(ics).endsWith("END:VCALENDAR\r\n");
        assertThat(ics.split("\r\n"))
                .allSatisfy(line -> assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75));
        String unfolded = ics.replace("\r\n ", "");
        assertThat(unfolded).contains("SUMMARY:" + summary + "\r\n");
        assertThat(Arrays.stream(ics.split("\r\n")).filter(l -> l.startsWith(" "))).isNotEmpty();
    }

    @Test
    void 종일_일정은_DATE_값으로_다음날까지() {
        String ics = new IcsWriter().beginCalendar("c")
                .allDayEvent("posting-1-deadline@hiretrack", LocalDate.of(2026, 11, 5), "[마감] A", null, NOW, NOW)
                .endCalendar().toString();

        assertThat(ics).contains("DTSTART;VALUE=DATE:20261105\r\n", "DTEND;VALUE=DATE:20261106\r\n", "TRANSP:TRANSPARENT");
    }
}
