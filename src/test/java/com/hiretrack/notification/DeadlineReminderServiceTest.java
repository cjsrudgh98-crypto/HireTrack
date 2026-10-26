package com.hiretrack.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.hiretrack.common.config.TimeConfig;
import com.hiretrack.notification.mail.ReminderMailer;
import com.hiretrack.posting.JobPostingRepository;
import com.hiretrack.posting.domain.ApplicationStatus;
import com.hiretrack.posting.domain.EmploymentType;
import com.hiretrack.posting.domain.JobPosting;
import com.hiretrack.posting.domain.PostingContent;
import com.hiretrack.user.User;
import com.hiretrack.user.UserRepository;

/**
 * 실제 DB(H2)로 대상 조회·유니크 제약을 검증한다. Clock 과 메일러는 직접 주입한다.
 * 다른 테스트가 만든 데이터와 섞이지 않도록 이 테스트에서 만든 사용자에게 간 메일만 본다.
 */
@SpringBootTest
class DeadlineReminderServiceTest {

    /** 2026-10-26 09:00 (서울) = 00:00 UTC */
    private static final Instant NINE_AM_SEOUL = Instant.parse("2026-10-26T00:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    @Autowired private JobPostingRepository postingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationSettingsRepository settingsRepository;
    @Autowired private NotificationLogRepository logRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final List<SentMail> outbox = new ArrayList<>();
    private boolean mailFails;

    record SentMail(String to, String subject, String body) {
    }

    private final ReminderMailer mailer = (to, subject, body) -> {
        if (mailFails) throw new IllegalStateException("SMTP down");
        outbox.add(new SentMail(to, subject, body));
    };

    @BeforeEach
    void clearOutbox() {
        outbox.clear();
        mailFails = false;
    }

    private DeadlineReminderService serviceAt(Instant instant) {
        return new DeadlineReminderService(postingRepository, settingsRepository, logRepository, mailer,
                new TransactionTemplate(transactionManager),
                new NotificationProperties("http://localhost:5173", "test@hiretrack.local"),
                meterRegistry, Clock.fixed(instant, TimeConfig.ZONE));
    }

    @Test
    void D3_D1_관심_공고만_사용자별로_한_통에_묶어_보낸다() {
        User user = newUser(); // 설정 없음 → 기본값(켜짐, D-3·D-1, 09시)
        posting(user, "D3테크", TODAY.plusDays(3), ApplicationStatus.INTERESTED);
        posting(user, "D1소프트", TODAY.plusDays(1), ApplicationStatus.INTERESTED);
        posting(user, "D2랩스", TODAY.plusDays(2), ApplicationStatus.INTERESTED);    // D-2 는 대상 아님
        posting(user, "지원함", TODAY.plusDays(3), ApplicationStatus.APPLIED);        // 이미 지원
        posting(user, "D7컴퍼니", TODAY.plusDays(7), ApplicationStatus.INTERESTED); // 기본 설정에 D-7 없음
        posting(user, "마감지남", TODAY.minusDays(1), ApplicationStatus.INTERESTED);

        serviceAt(NINE_AM_SEOUL).sendDueReminders();

        List<SentMail> mails = mailsTo(user);
        assertThat(mails).hasSize(1);
        assertThat(mails.getFirst().subject()).isEqualTo("[HireTrack] 마감 임박 공고 2건");
        assertThat(mails.getFirst().body())
                .contains("D-1  D1소프트 · 백엔드 개발자    (10/27 마감)")
                .contains("D-3  D3테크 · 백엔드 개발자    (10/29 마감)")
                .doesNotContain("D2랩스", "지원함", "D7컴퍼니", "마감지남")
                .contains("http://localhost:5173/");
        assertThat(mails.getFirst().body().indexOf("D-1")).isLessThan(mails.getFirst().body().indexOf("D-3"));
        assertThat(logRepository.findByUserId(user.getId())).hasSize(2);
    }

    @Test
    void 두_번_실행해도_같은_알림은_다시_보내지_않는다() {
        User user = newUser();
        posting(user, "중복확인", TODAY.plusDays(1), ApplicationStatus.INTERESTED);

        serviceAt(NINE_AM_SEOUL).sendDueReminders();
        serviceAt(NINE_AM_SEOUL.plusSeconds(30)).sendDueReminders();

        assertThat(mailsTo(user)).hasSize(1);
        assertThat(logRepository.findByUserId(user.getId())).hasSize(1);
    }

    @Test
    void 유니크_제약이_동시_실행으로_인한_중복_기록을_막는다() {
        User user = newUser();
        JobPosting posting = posting(user, "동시실행", TODAY.plusDays(1), ApplicationStatus.INTERESTED);
        // 다른 서버가 먼저 기록한 상황
        logRepository.save(new NotificationLog(user.getId(), posting.getId(), 1, NINE_AM_SEOUL));

        serviceAt(NINE_AM_SEOUL).sendDueReminders();

        assertThat(mailsTo(user)).isEmpty();
    }

    @Test
    void 알림을_끄거나_다른_시각을_고른_사용자는_제외() {
        User off = newUser();
        settingsRepository.save(new NotificationSettings(off.getId(), false, List.of(3, 1), 9));
        posting(off, "꺼짐", TODAY.plusDays(1), ApplicationStatus.INTERESTED);

        User tenAm = newUser();
        settingsRepository.save(new NotificationSettings(tenAm.getId(), true, List.of(7, 3, 1), 10));
        posting(tenAm, "열시", TODAY.plusDays(7), ApplicationStatus.INTERESTED);

        serviceAt(NINE_AM_SEOUL).sendDueReminders();
        assertThat(mailsTo(off)).isEmpty();
        assertThat(mailsTo(tenAm)).isEmpty();

        serviceAt(NINE_AM_SEOUL.plusSeconds(3600)).sendDueReminders(); // 10:00
        assertThat(mailsTo(tenAm)).singleElement().satisfies(m -> assertThat(m.body()).contains("D-7  열시"));
    }

    @Test
    void D_day는_UTC가_아니라_서울_날짜로_계산한다() {
        // 서울 10/27 00:30 = UTC 10/26 15:30. UTC 날짜로 계산하면 10/28 마감이 D-2 가 돼 누락된다.
        User user = newUser();
        settingsRepository.save(new NotificationSettings(user.getId(), true, List.of(1), 0));
        posting(user, "자정직후", LocalDate.of(2026, 10, 28), ApplicationStatus.INTERESTED);

        serviceAt(Instant.parse("2026-10-26T15:30:00Z")).sendDueReminders();

        assertThat(mailsTo(user)).singleElement().satisfies(m -> assertThat(m.body()).contains("D-1  자정직후"));
    }

    @Test
    void 메일_발송이_실패하면_기록을_남기지_않아_다음_실행에서_다시_보낸다() {
        User user = newUser();
        posting(user, "재시도", TODAY.plusDays(3), ApplicationStatus.INTERESTED);

        mailFails = true;
        serviceAt(NINE_AM_SEOUL).sendDueReminders();
        assertThat(logRepository.findByUserId(user.getId())).isEmpty();

        mailFails = false;
        serviceAt(NINE_AM_SEOUL.plusSeconds(60)).sendDueReminders();
        assertThat(mailsTo(user)).hasSize(1);
        // 운영 지표: 실패 1건, 성공 1건
        assertThat(meterRegistry.counter("hiretrack.reminders", "result", "failed").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("hiretrack.reminders", "result", "sent").count()).isEqualTo(1);
    }

    private User newUser() {
        return userRepository.save(new User("remind-" + UUID.randomUUID() + "@test.com", "pw", NINE_AM_SEOUL));
    }

    private JobPosting posting(User user, String company, LocalDate deadline, ApplicationStatus status) {
        JobPosting posting = new JobPosting(user,
                new PostingContent(company, "백엔드 개발자", null, "서울", EmploymentType.FULL_TIME, deadline, null),
                null, NINE_AM_SEOUL);
        posting.getApplication().changeStatus(status, NINE_AM_SEOUL);
        return postingRepository.save(posting);
    }

    private List<SentMail> mailsTo(User user) {
        return outbox.stream().filter(m -> m.to().equals(user.getEmail())).toList();
    }
}
