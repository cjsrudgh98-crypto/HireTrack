package com.hiretrack.notification;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.hiretrack.common.config.TimeConfig;
import com.hiretrack.notification.mail.ReminderMailer;
import com.hiretrack.posting.JobPostingRepository;
import com.hiretrack.posting.domain.ApplicationStatus;
import com.hiretrack.posting.domain.JobPosting;
import com.hiretrack.user.User;

/**
 * 마감 임박(D-7/D-3/D-1) "관심" 공고를 사용자별로 묶어 하루 한 통 보낸다.
 * 날짜·시각 계산은 서버 시간대와 상관없이 항상 서울 기준이다(UTC 서버에서 D-day 가 하루 밀리는 문제 방지).
 */
@Service
public class DeadlineReminderService {

    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderService.class);
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM/dd");

    private final JobPostingRepository postingRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final NotificationLogRepository logRepository;
    private final ReminderMailer mailer;
    private final TransactionTemplate transactionTemplate;
    private final NotificationProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public DeadlineReminderService(JobPostingRepository postingRepository,
                                   NotificationSettingsRepository settingsRepository,
                                   NotificationLogRepository logRepository,
                                   ReminderMailer mailer,
                                   TransactionTemplate transactionTemplate,
                                   NotificationProperties properties,
                                   MeterRegistry meterRegistry,
                                   Clock clock) {
        this.postingRepository = postingRepository;
        this.settingsRepository = settingsRepository;
        this.logRepository = logRepository;
        this.mailer = mailer;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    /** @return 이번 실행에서 보낸 메일 수 */
    public int sendDueReminders() {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(TimeConfig.ZONE));
        LocalDate today = now.toLocalDate();

        // 마감일 → D-n
        Map<LocalDate, Integer> daysByDeadline = NotificationSettings.ALLOWED_DAYS.stream()
                .collect(Collectors.toMap(days -> today.plusDays(days), Function.identity()));

        List<JobPosting> candidates = postingRepository.findReminderCandidates(
                ApplicationStatus.INTERESTED, daysByDeadline.keySet());
        if (candidates.isEmpty()) return 0;

        Set<String> alreadySent = logRepository.findByPostingIdIn(candidates.stream().map(JobPosting::getId).toList())
                .stream().map(l -> key(l.getPostingId(), l.getRemindDay())).collect(Collectors.toSet());
        Map<Long, NotificationSettings> settingsByUser = settingsRepository.findAllById(
                        candidates.stream().map(p -> p.getUser().getId()).distinct().toList())
                .stream().collect(Collectors.toMap(NotificationSettings::getUserId, Function.identity()));

        Map<Recipient, List<ReminderItem>> byUser = new LinkedHashMap<>();
        for (JobPosting posting : candidates) {
            User user = posting.getUser();
            NotificationSettings settings = settingsByUser.getOrDefault(user.getId(), NotificationSettings.defaults(user.getId()));
            int days = daysByDeadline.get(posting.getDeadline());
            if (!settings.shouldSendAt(now.getHour()) || !settings.getRemindDayList().contains(days)) continue;
            if (alreadySent.contains(key(posting.getId(), days))) continue;
            byUser.computeIfAbsent(new Recipient(user.getId(), user.getEmail()), r -> new ArrayList<>())
                    .add(ReminderItem.of(posting, days));
        }

        int sent = 0;
        for (Map.Entry<Recipient, List<ReminderItem>> entry : byUser.entrySet()) {
            if (sendTo(entry.getKey(), entry.getValue())) sent++;
        }
        return sent;
    }

    /**
     * 발송 기록 저장과 메일 발송을 한 트랜잭션으로 묶는다.
     * 기록을 먼저 넣어 유니크 제약으로 동시 실행을 걸러내고, 메일이 실패하면 기록도 롤백돼 다음 실행 때 다시 시도한다.
     */
    private boolean sendTo(Recipient user, List<ReminderItem> items) {
        List<ReminderItem> sorted = items.stream()
                .sorted(Comparator.comparingInt(ReminderItem::days).thenComparing(ReminderItem::deadline))
                .toList();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                for (ReminderItem item : sorted) {
                    logRepository.saveAndFlush(new NotificationLog(user.userId(), item.postingId(), item.days(), clock.instant()));
                }
                mailer.send(user.email(), subject(sorted), body(sorted, properties.baseUrl()));
            });
            log.info("reminder-sent userId={} postings={}", user.userId(), sorted.size());
            count("sent");
            return true;
        } catch (DataIntegrityViolationException e) {
            log.info("reminder-skipped-duplicate userId={}", user.userId());
            count("duplicate");
            return false;
        } catch (RuntimeException e) {
            log.error("reminder-failed userId={}", user.userId(), e);
            count("failed");
            return false;
        }
    }

    /** hiretrack.reminders{result=sent|failed|duplicate} — 발송 성공·실패 건수 지표 */
    private void count(String result) {
        Counter.builder("hiretrack.reminders").description("마감 알림 메일 발송 결과")
                .tag("result", result).register(meterRegistry).increment();
    }

    static String subject(List<ReminderItem> items) {
        return "[HireTrack] 마감 임박 공고 " + items.size() + "건";
    }

    static String body(List<ReminderItem> items, String baseUrl) {
        StringBuilder body = new StringBuilder("마감이 다가오는 관심 공고가 있어요.\n\n");
        for (ReminderItem item : items) {
            body.append(String.format("D-%d  %s · %s    (%s 마감)",
                    item.days(), item.company(), item.title(), item.deadline().format(MONTH_DAY))).append('\n');
        }
        body.append("\n→ 지원 현황 보러가기: ").append(baseUrl).append('/')
                .append("\n\n알림 설정 변경: ").append(baseUrl).append("/settings\n");
        return body.toString();
    }

    private static String key(Long postingId, int days) {
        return postingId + ":" + days;
    }

    private record Recipient(Long userId, String email) {
    }

    record ReminderItem(Long postingId, String company, String title, LocalDate deadline, int days) {

        static ReminderItem of(JobPosting posting, int days) {
            return new ReminderItem(posting.getId(), posting.getCompany(), posting.getTitle(), posting.getDeadline(), days);
        }
    }
}
