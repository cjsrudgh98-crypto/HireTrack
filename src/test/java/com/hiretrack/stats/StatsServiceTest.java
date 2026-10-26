package com.hiretrack.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hiretrack.common.config.TimeConfig;
import com.hiretrack.common.error.BusinessException;
import com.hiretrack.essay.AnswerTemplateRepository;
import com.hiretrack.essay.EssayAnswerRepository;
import com.hiretrack.essay.EssayQuestionRepository;
import com.hiretrack.essay.domain.AnswerTemplate;
import com.hiretrack.essay.domain.EssayAnswer;
import com.hiretrack.essay.domain.EssayCategory;
import com.hiretrack.essay.domain.EssayQuestion;
import com.hiretrack.essay.domain.Submission;
import com.hiretrack.posting.JobPostingRepository;
import com.hiretrack.posting.domain.ApplicationStatus;
import com.hiretrack.posting.domain.EmploymentType;
import com.hiretrack.posting.domain.JobPosting;
import com.hiretrack.posting.domain.PostingContent;
import com.hiretrack.stats.dto.StatsDtos.BreakdownRow;
import com.hiretrack.stats.dto.StatsDtos.FunnelStep;
import com.hiretrack.stats.dto.StatsDtos.Ratio;
import com.hiretrack.stats.dto.StatsDtos.ResponseTimeResponse;
import com.hiretrack.stats.dto.StatsDtos.SummaryResponse;
import com.hiretrack.stats.dto.StatsDtos.WeeklyPoint;
import com.hiretrack.user.User;
import com.hiretrack.user.UserRepository;

import static com.hiretrack.posting.domain.ApplicationStatus.ACCEPTED;
import static com.hiretrack.posting.domain.ApplicationStatus.APPLIED;
import static com.hiretrack.posting.domain.ApplicationStatus.DOCUMENT_PASSED;
import static com.hiretrack.posting.domain.ApplicationStatus.INTERVIEW;
import static com.hiretrack.posting.domain.ApplicationStatus.REJECTED;

/**
 * 시나리오 데이터로 숫자를 검증한다. (날짜는 모두 2026년 10월, 서울 기준 10시)
 *
 * <pre>
 *  A  관심만                                                       → 지원 아님
 *  B  10/01 지원                                                    → 서류 결과 대기
 *  C  10/01 지원 → 10/05 불합격                                     → 서류 탈락 (응답 4일)
 *  D  10/02 지원 → 10/04 서류합격 → 10/10 면접 → 10/15 불합격       → 서류 통과로 집계! (응답 2일)
 *  E  10/03 지원 → 10/13 서류합격 → 10/16 면접 → 10/20 최종합격     → (응답 10일)
 *  F  10/05 지원 → 10/05 서류합격(1분 뒤 되돌림)                    → 되돌린 이력 무시, 결과 대기
 *  G  10/06 관심 → 면접 (단계 건너뜀)                               → 지원·서류 통과로 집계, 응답 기간은 제외
 * </pre>
 */
@SpringBootTest
class StatsServiceTest {

    @Autowired private StatsQueryRepository statsQueryRepository;
    @Autowired private AnswerTemplateRepository templateRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JobPostingRepository postingRepository;
    @Autowired private EssayQuestionRepository questionRepository;
    @Autowired private EssayAnswerRepository answerRepository;

    private StatsService stats;
    private User user;
    private AnswerTemplate template;

    @BeforeEach
    void scenario() {
        stats = new StatsService(statsQueryRepository, templateRepository,
                Clock.fixed(Instant.parse("2026-10-26T01:00:00Z"), TimeConfig.ZONE));
        user = userRepository.save(new User("stats-" + UUID.randomUUID() + "@test.com", "pw", at(9, 1)));
        template = templateRepository.save(new AnswerTemplate(user, EssayCategory.MOTIVATION, "데이터로 문제 해결", "...", at(9, 1)));

        posting("A", EmploymentType.FULL_TIME, "서울 강남구");
        JobPosting b = posting("B", EmploymentType.FULL_TIME, "서울 강남구", APPLIED, at(10, 1));
        JobPosting c = posting("C", EmploymentType.FULL_TIME, "서울 마포구", APPLIED, at(10, 1), REJECTED, at(10, 5));
        JobPosting d = posting("D", EmploymentType.FULL_TIME, "경기 성남시",
                APPLIED, at(10, 2), DOCUMENT_PASSED, at(10, 4), INTERVIEW, at(10, 10), REJECTED, at(10, 15));
        JobPosting e = posting("E", EmploymentType.FREELANCE, "원격",
                APPLIED, at(10, 3), DOCUMENT_PASSED, at(10, 13), INTERVIEW, at(10, 16), ACCEPTED, at(10, 20));
        posting("F", EmploymentType.FREELANCE, null,
                APPLIED, at(10, 5), DOCUMENT_PASSED, at(10, 5).plusSeconds(60), APPLIED, at(10, 5).plusSeconds(120));
        posting("G", null, null, INTERVIEW, at(10, 6));

        // 템플릿을 쓴 제출본: C(서류 탈락), D·E(서류 통과) / 템플릿 없는 제출본: B
        submittedAnswer(c, template.getId());
        submittedAnswer(d, template.getId());
        submittedAnswer(e, template.getId());
        submittedAnswer(b, null);
    }

    @Test
    void 요약_결과가_나온_건_기준과_전체_기준을_나눠서_센다() {
        SummaryResponse summary = stats.summary(user.getId());

        assertThat(summary.totalPostings()).isEqualTo(7);
        assertThat(summary.totalApplied()).isEqualTo(6);              // A 제외
        // 서류: 통과 D·E·G / 탈락 C / 대기 B·F
        assertThat(summary.documentPass().decided()).isEqualTo(Ratio.of(3, 4));
        assertThat(summary.documentPass().overall()).isEqualTo(Ratio.of(3, 6));
        assertThat(summary.documentPass().pending()).isEqualTo(2);
        // 면접 전환: 서류 통과 3건 모두 면접 도달
        assertThat(summary.interview().decided()).isEqualTo(Ratio.of(3, 3));
        // 최종: 면접 3건 중 합격 E / 탈락 D / 대기 G
        assertThat(summary.finalAccept().decided()).isEqualTo(Ratio.of(1, 2));
        assertThat(summary.finalAccept().overall()).isEqualTo(Ratio.of(1, 3));
        assertThat(summary.finalAccept().pending()).isEqualTo(1);
        assertThat(summary.acceptedCount()).isEqualTo(1);
    }

    @Test
    void 퍼널은_현재_상태가_아니라_도달_여부로_센다() {
        List<FunnelStep> funnel = stats.funnel(user.getId());

        // D 는 현재 REJECTED 지만 서류 합격·면접 단계에 포함된다. F 의 되돌린 서류 합격은 빠진다.
        assertThat(funnel).extracting(FunnelStep::count).containsExactly(6, 3, 3, 1);
        assertThat(funnel.get(0).conversion()).isNull();
        assertThat(funnel.get(1).conversion()).isEqualTo(Ratio.of(3, 6));
        assertThat(funnel.get(3).conversion()).isEqualTo(Ratio.of(1, 3));
    }

    @Test
    void 응답까지_걸린_기간은_지원_기록이_있는_건만_서울_날짜로_계산한다() {
        ResponseTimeResponse response = stats.responseTime(user.getId());

        assertThat(response.count()).isEqualTo(3);            // C 4일, D 2일, E 10일 (F 되돌림·G 건너뜀 제외)
        assertThat(response.averageDays()).isEqualTo(16 / 3.0);
        assertThat(response.medianDays()).isEqualTo(4.0);
        assertThat(response.waiting()).isEqualTo(2);           // B, F
        assertThat(response.buckets()).extracting(b -> b.count()).containsExactly(1, 1, 1, 0, 0);
    }

    @Test
    void 주간_지원_추이는_빈_주도_0으로_채운다() {
        List<WeeklyPoint> weekly = stats.weekly(user.getId(), LocalDate.of(2026, 9, 30));

        assertThat(weekly).extracting(WeeklyPoint::weekStart).containsExactly(
                LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26));
        assertThat(weekly).extracting(WeeklyPoint::count).containsExactly(4, 2, 0, 0, 0);
        assertThatThrownBy(() -> stats.weekly(user.getId(), LocalDate.of(2026, 12, 1))).isInstanceOf(BusinessException.class);
    }

    @Test
    void 고용형태별_지역별_템플릿별_서류_통과율() {
        List<BreakdownRow> byType = stats.breakdown(user.getId(), "employmentType").rows();
        assertThat(row(byType, "정규직")).satisfies(r -> {
            assertThat(r.applied()).isEqualTo(3);                      // B C D
            assertThat(r.decided()).isEqualTo(Ratio.of(1, 2));
            assertThat(r.overall()).isEqualTo(Ratio.of(1, 3));
            assertThat(r.pending()).isEqualTo(1);
        });
        assertThat(row(byType, "프리랜서").decided()).isEqualTo(Ratio.of(1, 1));
        assertThat(row(byType, "미지정").applied()).isEqualTo(1);

        List<BreakdownRow> byRegion = stats.breakdown(user.getId(), "location").rows();
        assertThat(row(byRegion, "서울").applied()).isEqualTo(2);        // B, C ("서울 강남구", "서울 마포구")
        assertThat(row(byRegion, "미지정").applied()).isEqualTo(2);      // F, G

        List<BreakdownRow> byTemplate = stats.breakdown(user.getId(), "template").rows();
        assertThat(row(byTemplate, "데이터로 문제 해결").decided()).isEqualTo(Ratio.of(2, 3)); // C 탈락, D·E 통과
        assertThat(row(byTemplate, "템플릿 미사용").applied()).isEqualTo(3);                 // B, F, G

        assertThatThrownBy(() -> stats.breakdown(user.getId(), "salary")).isInstanceOf(BusinessException.class);
    }

    @Test
    void 다른_사용자의_데이터는_섞이지_않는다() {
        User stranger = userRepository.save(new User("stats-" + UUID.randomUUID() + "@test.com", "pw", at(9, 1)));

        assertThat(stats.summary(stranger.getId()).totalPostings()).isZero();
        assertThat(stats.summary(stranger.getId()).documentPass().decided().rate()).isNull(); // 0% 가 아니라 "데이터 없음"
    }

    // ---------- helpers ----------

    private static Instant at(int month, int day) {
        return LocalDate.of(2026, month, day).atTime(LocalTime.of(10, 0)).atZone(TimeConfig.ZONE).toInstant();
    }

    /** statusesAndTimes: 상태, 시각, 상태, 시각 ... */
    private JobPosting posting(String company, EmploymentType type, String location, Object... statusesAndTimes) {
        JobPosting posting = new JobPosting(user,
                new PostingContent(company, "개발자", null, location, type, null, null), null, at(9, 20));
        for (int i = 0; i < statusesAndTimes.length; i += 2) {
            posting.getApplication().changeStatus((ApplicationStatus) statusesAndTimes[i], (Instant) statusesAndTimes[i + 1]);
        }
        return postingRepository.save(posting);
    }

    private void submittedAnswer(JobPosting posting, Long templateId) {
        EssayQuestion question = questionRepository.save(
                new EssayQuestion(posting, "지원동기", 500, EssayCategory.MOTIVATION, 1, at(9, 25)));
        EssayAnswer answer = new EssayAnswer(question, 1, "답변", templateId, at(9, 25));
        Submission.freeze(answer);
        answerRepository.save(answer);
    }

    private static BreakdownRow row(List<BreakdownRow> rows, String label) {
        return rows.stream().filter(r -> r.label().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError("no row " + label + " in " + rows));
    }
}
