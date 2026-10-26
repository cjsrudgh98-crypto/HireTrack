package com.hiretrack.parse.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.hiretrack.parse.Fixtures;
import com.hiretrack.parse.ParsedPosting;
import com.hiretrack.posting.domain.EmploymentType;

import tools.jackson.databind.json.JsonMapper;

class ParsersTest {

    @Test
    void 사람인_전용_파서는_헤더와_요약_영역에서_모든_항목을_읽는다() {
        var doc = Fixtures.load("saramin.html", "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=1");

        ParsedPosting parsed = new SaraminParser(Fixtures.CLOCK).parse(doc);

        assertThat(parsed.company()).isEqualTo("○○테크");
        assertThat(parsed.title()).isEqualTo("백엔드 개발자 (Java/Spring)");
        assertThat(parsed.location()).isEqualTo("서울 강남구");
        assertThat(parsed.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(parsed.deadline()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(parsed.requirements())
                .contains("경력: 신입 · 경력 3년↑")
                .contains("학력: 대졸(4년제) 이상")
                .contains("우대사항: AWS 운영 경험");
        assertThat(parsed.hasAllFields()).isTrue();
    }

    @Test
    void 잡코리아_전용_파서는_제목에서_회사명을_빼고_접수기간의_마지막_날짜를_마감일로_쓴다() {
        var doc = Fixtures.load("jobkorea.html", "https://www.jobkorea.co.kr/Recruit/GI_Read/1");

        ParsedPosting parsed = new JobKoreaParser(Fixtures.CLOCK).parse(doc);

        assertThat(parsed.company()).isEqualTo("△△소프트");
        assertThat(parsed.title()).isEqualTo("풀스택 개발자 (React / Node.js)");
        assertThat(parsed.location()).isEqualTo("경기 성남시 분당구");
        assertThat(parsed.employmentType()).isEqualTo(EmploymentType.FREELANCE);
        assertThat(parsed.deadline()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(parsed.requirements()).contains("스킬: React, TypeScript, Node.js");
    }

    @Test
    void 자바스크립트로_그리는_페이지는_전용_파서가_빈_결과를_낸다() {
        var doc = Fixtures.load("jobkorea-js-rendered.html", "https://www.jobkorea.co.kr/Recruit/GI_Read/2");

        assertThat(new JobKoreaParser(Fixtures.CLOCK).parse(doc).hasCore()).isFalse();
    }

    @Test
    void JSON_LD_파서는_graph_안의_JobPosting을_찾고_마감일을_서울_날짜로_바꾼다() {
        var doc = Fixtures.load("jsonld.html", "https://careers.example.com/jobs/7");

        ParsedPosting parsed = new JsonLdParser(JsonMapper.builder().build(), Fixtures.CLOCK).parse(doc);

        assertThat(parsed.company()).isEqualTo("◇◇랩스");
        assertThat(parsed.title()).isEqualTo("Android 개발자");
        assertThat(parsed.employmentType()).isEqualTo(EmploymentType.FREELANCE);
        assertThat(parsed.location()).isEqualTo("원격");
        // 2026-11-05T15:30Z 는 서울 기준 11/06 00:30 → UTC 날짜로 자르면 하루 어긋난다
        assertThat(parsed.deadline()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(parsed.requirements()).isEqualTo("Kotlin 실무 경험 3년 이상 Jetpack Compose 경험");
    }

    @Test
    void Open_Graph_파서는_제목에서_사이트명_꼬리를_떼고_site_name을_회사명으로_쓴다() {
        var doc = Fixtures.load("opengraph.html", "https://careers.example.com/fe");

        ParsedPosting parsed = new OpenGraphParser().parse(doc);

        assertThat(parsed.title()).isEqualTo("프론트엔드 엔지니어");
        assertThat(parsed.company()).isEqualTo("☆☆커머스");
        assertThat(parsed.deadline()).isNull();
    }

    @Test
    void Open_Graph_파서는_채용_플랫폼_이름을_회사명으로_쓰지_않고_대괄호_회사명을_쓴다() {
        var doc = Fixtures.load("saramin.html", "https://www.saramin.co.kr/x");

        ParsedPosting parsed = new OpenGraphParser().parse(doc);

        assertThat(parsed.company()).isEqualTo("○○테크");
        assertThat(parsed.title()).isEqualTo("백엔드 개발자 (Java/Spring) 채용");
    }

    @Test
    void 정보가_없는_페이지는_모든_파서가_빈_결과() {
        var doc = Fixtures.load("empty.html", "https://spa.example.com/");

        assertThat(Fixtures.parsers()).allSatisfy(p -> assertThat(p.parse(doc).hasCore()).isFalse());
    }
}
