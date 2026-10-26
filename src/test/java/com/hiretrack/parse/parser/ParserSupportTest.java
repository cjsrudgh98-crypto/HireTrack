package com.hiretrack.parse.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.hiretrack.posting.domain.EmploymentType;

class ParserSupportTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2026.11.05 23:59                 | 2026-11-05",
            "2026-11-05                       | 2026-11-05",
            "2026/11/5                        | 2026-11-05",
            "2026년 11월 5일                   | 2026-11-05",
            "2026.10.20 ~ 2026.11.05          | 2026-11-05",
            "~ 11/05(목)                       | 2026-11-05",
            "11월 5일 마감                      | 2026-11-05",
            "~ 01/10(일)                       | 2027-01-10",
    })
    void 마감일_형식을_해석한다(String text, LocalDate expected) {
        assertThat(ParserSupport.deadline(text, TODAY)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"상시채용", "채용시 마감", "마감일 미정", "2026.13.45"})
    void 마감일이_없거나_해석할_수_없으면_null(String text) {
        assertThat(ParserSupport.deadline(text, TODAY)).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "정규직 (수습 3개월) | FULL_TIME",
            "프리랜서           | FREELANCE",
            "계약직, 인턴        | OTHER",
    })
    void 고용형태를_해석한다(String text, EmploymentType expected) {
        assertThat(ParserSupport.employmentType(text)).isEqualTo(expected);
    }
}
