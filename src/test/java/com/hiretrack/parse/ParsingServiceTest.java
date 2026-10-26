package com.hiretrack.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import com.hiretrack.parse.dto.ParseResponse;
import com.hiretrack.parse.fetch.FetchException;
import com.hiretrack.parse.fetch.UrlGuard;
import com.hiretrack.posting.domain.EmploymentType;

class ParsingServiceTest {

    private static final Long USER_ID = 1L;

    private final ParserStats stats = new ParserStats();
    /** 모든 호스트를 공인 IP 로 해석하는 가짜 DNS — 네트워크 없이 검증 통과 */
    private final UrlGuard urlGuard = new UrlGuard(host -> List.of(InetAddress.getByAddress(host, new byte[]{93, (byte) 184, (byte) 216, 34})));
    private Document page;
    private RuntimeException fetchError;
    private ParsingService service;

    @BeforeEach
    void setUp() {
        ParseRateLimiter rateLimiter = new ParseRateLimiter(
                new ParseProperties(Duration.ofSeconds(5), DataSize.ofMegabytes(2), 100), Fixtures.CLOCK);
        service = new ParsingService(Fixtures.parsers(), urlGuard, uri -> {
            if (fetchError != null) throw fetchError;
            return page;
        }, rateLimiter, stats);
    }

    @Test
    void 사이트_전용_파서가_있으면_그_결과를_쓴다() {
        page = Fixtures.load("saramin.html", "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=1");

        ParseResponse response = service.parse(USER_ID, "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=1");

        assertThat(response.success()).isTrue();
        assertThat(response.source()).isEqualTo(ParseSource.SARAMIN);
        assertThat(response.data().company()).isEqualTo("○○테크");
        assertThat(response.missingFields()).isEmpty();
    }

    @Test
    void 전용_파서가_실패하면_JSON_LD로_넘어가고_실패가_집계된다() {
        page = Fixtures.load("jobkorea-js-rendered.html", "https://www.jobkorea.co.kr/Recruit/GI_Read/2");

        ParseResponse response = service.parse(USER_ID, "https://www.jobkorea.co.kr/Recruit/GI_Read/2");

        assertThat(response.success()).isTrue();
        assertThat(response.source()).isEqualTo(ParseSource.JSON_LD);
        assertThat(response.data().company()).isEqualTo("□□데이터");
        assertThat(response.data().title()).isEqualTo("데이터 엔지니어");
        assertThat(response.data().location()).isEqualTo("서울 마포구");
        assertThat(response.data().employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(response.missingFields()).containsExactly("requirements");
        assertThat(stats.failures(ParseSource.JOBKOREA)).isEqualTo(1);
    }

    @Test
    void 잡코리아_현재_화면은_전용_파서의_메타_정보와_JSON_LD의_근무지_고용형태를_합쳐_모든_칸을_채운다() {
        page = Fixtures.load("jobkorea-current.html", "https://www.jobkorea.co.kr/Recruit/GI_Read/3");

        ParseResponse response = service.parse(USER_ID, "https://www.jobkorea.co.kr/Recruit/GI_Read/3");

        assertThat(response.success()).isTrue();
        assertThat(response.source()).isEqualTo(ParseSource.JOBKOREA);
        assertThat(response.data().location()).isEqualTo("서울 송파구 법원로 100");
        assertThat(response.data().employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(response.data().requirements()).isEqualTo("경력: 경력 3년이상\n학력: 대졸↑");
        assertThat(response.missingFields()).isEmpty();
        assertThat(stats.failures(ParseSource.JOBKOREA)).isZero();
    }

    @Test
    void 사람인_껍데기_페이지는_근무지역과_고용형태만_비어_있다고_알려준다() {
        page = Fixtures.load("saramin-shell.html", "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=1");

        ParseResponse response = service.parse(USER_ID, "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=1");

        assertThat(response.success()).isTrue();
        assertThat(response.source()).isEqualTo(ParseSource.SARAMIN);
        assertThat(response.data().deadline()).isEqualTo(java.time.LocalDate.of(2026, 11, 20));
        assertThat(response.missingFields()).containsExactlyInAnyOrder("location", "employmentType");
        assertThat(stats.failures(ParseSource.SARAMIN)).isZero();
    }

    @Test
    void 구조화_데이터가_없으면_Open_Graph로_제목만이라도_채운다() {
        page = Fixtures.load("opengraph.html", "https://careers.example.com/fe");

        ParseResponse response = service.parse(USER_ID, "https://careers.example.com/fe");

        assertThat(response.success()).isTrue();
        assertThat(response.source()).isEqualTo(ParseSource.OPEN_GRAPH);
        assertThat(response.missingFields())
                .containsExactly("location", "employmentType", "deadline", "requirements");
    }

    @Test
    void 아무것도_못_찾으면_실패_안내와_빈_결과() {
        page = Fixtures.load("empty.html", "https://spa.example.com/");

        ParseResponse response = service.parse(USER_ID, "https://spa.example.com/");

        assertThat(response.success()).isFalse();
        assertThat(response.source()).isEqualTo(ParseSource.NONE);
        assertThat(response.message()).isEqualTo(ParsingService.FAILURE_MESSAGE);
        assertThat(response.missingFields()).hasSize(6);
    }

    @Test
    void 페이지를_못_가져오면_실패_응답으로_수동_입력에_넘긴다() {
        fetchError = new FetchException("응답 시간이 초과되었습니다.");

        ParseResponse response = service.parse(USER_ID, "https://slow.example.com/");

        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("직접 입력해 주세요").contains("응답 시간이 초과");
    }
}
