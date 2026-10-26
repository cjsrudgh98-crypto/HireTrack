package com.hiretrack.posting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.hiretrack.user.User;

class ApplicationTest {

    private static final Instant T0 = Instant.parse("2026-10-20T01:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-21T01:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-22T01:00:00Z");
    private static final Instant T3 = Instant.parse("2026-10-23T01:00:00Z");

    private Application newApplication() {
        User user = new User("me@test.com", "pw", T0);
        PostingContent content = new PostingContent("○○테크", "백엔드 개발자", null, "서울", EmploymentType.FULL_TIME, null, null);
        return new JobPosting(user, content, null, T0).getApplication();
    }

    @Test
    void 공고를_등록하면_관심_상태와_등록_이력이_생성된다() {
        Application application = newApplication();

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.INTERESTED);
        assertThat(application.getAppliedAt()).isNull();
        assertThat(application.getHistories()).singleElement().satisfies(h -> {
            assertThat(h.getFromStatus()).isNull();
            assertThat(h.getToStatus()).isEqualTo(ApplicationStatus.INTERESTED);
        });
    }

    @Test
    void 상태를_바꾸면_이전_이후_상태가_이력으로_남는다() {
        Application application = newApplication();

        boolean changed = application.changeStatus(ApplicationStatus.APPLIED, T1).changed();

        assertThat(changed).isTrue();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(application.getHistories()).hasSize(2).last().satisfies(h -> {
            assertThat(h.getFromStatus()).isEqualTo(ApplicationStatus.INTERESTED);
            assertThat(h.getToStatus()).isEqualTo(ApplicationStatus.APPLIED);
            assertThat(h.getChangedAt()).isEqualTo(T1);
        });
        assertThat(application.getPosting().getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void 지원일은_APPLIED로_처음_바뀔_때만_기록된다() {
        Application application = newApplication();

        application.changeStatus(ApplicationStatus.APPLIED, T1);
        application.changeStatus(ApplicationStatus.INTERESTED, T2);
        application.changeStatus(ApplicationStatus.APPLIED, T3);

        assertThat(application.getAppliedAt()).isEqualTo(T1);
        assertThat(application.getHistories()).hasSize(4);
    }

    @Test
    void 같은_상태로의_변경은_무시하고_이력도_남기지_않는다() {
        Application application = newApplication();

        boolean changed = application.changeStatus(ApplicationStatus.INTERESTED, T1).changed();

        assertThat(changed).isFalse();
        assertThat(application.getHistories()).hasSize(1);
        assertThat(application.getPosting().getUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void 첫_지원이면_firstApplied() {
        Application application = newApplication();

        assertThat(application.changeStatus(ApplicationStatus.APPLIED, T1).firstApplied()).isTrue();
        application.changeStatus(ApplicationStatus.DOCUMENT_PASSED, T2);
        application.changeStatus(ApplicationStatus.APPLIED, T3);
        assertThat(application.changeStatus(ApplicationStatus.INTERVIEW, T3.plusSeconds(3600)).firstApplied()).isFalse();
    }

    @Test
    void 직전_변경을_10분_안에_되돌리면_두_이력_모두_reverted로_표시하고_지원일도_지운다() {
        Application application = newApplication();
        application.changeStatus(ApplicationStatus.APPLIED, T1);

        StatusChange undo = application.changeStatus(ApplicationStatus.INTERESTED, T1.plusSeconds(120));

        assertThat(undo.applyReverted()).isTrue();
        assertThat(application.getAppliedAt()).isNull();
        assertThat(application.getHistories()).extracting(StatusHistory::isReverted).containsExactly(false, true, true);

        // 다시 지원하면 그때가 첫 지원
        assertThat(application.changeStatus(ApplicationStatus.APPLIED, T2).firstApplied()).isTrue();
        assertThat(application.getAppliedAt()).isEqualTo(T2);
    }

    @Test
    void 서류합격으로_잘못_옮겼다가_되돌리면_서류합격_이력은_통계에서_빠진다() {
        Application application = newApplication();
        application.changeStatus(ApplicationStatus.APPLIED, T1);
        application.changeStatus(ApplicationStatus.DOCUMENT_PASSED, T2);

        StatusChange undo = application.changeStatus(ApplicationStatus.APPLIED, T2.plusSeconds(60));

        assertThat(undo.applyReverted()).isFalse();
        assertThat(application.getAppliedAt()).isEqualTo(T1);
        assertThat(application.getHistories())
                .filteredOn(h -> h.getToStatus() == ApplicationStatus.DOCUMENT_PASSED)
                .allMatch(StatusHistory::isReverted);
    }

    @Test
    void 시간이_지난_뒤의_되돌림이나_다른_상태로의_이동은_정상_이력이다() {
        Application application = newApplication();
        application.changeStatus(ApplicationStatus.APPLIED, T1);
        application.changeStatus(ApplicationStatus.INTERESTED, T1.plus(Application.REVERT_WINDOW).plusSeconds(1));
        application.changeStatus(ApplicationStatus.REJECTED, T2);

        assertThat(application.getHistories()).noneMatch(StatusHistory::isReverted);
        assertThat(application.getAppliedAt()).isEqualTo(T1);
    }

    @Test
    void 불합격은_어느_단계에서든_이동할_수_있다() {
        Application application = newApplication();

        application.changeStatus(ApplicationStatus.REJECTED, T1);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(application.getAppliedAt()).isNull();
    }
}
