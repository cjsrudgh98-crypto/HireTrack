package com.hiretrack.calendar;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hiretrack.calendar.CalendarDtos.CalendarSubscription;
import com.hiretrack.common.config.TimeConfig;
import com.hiretrack.common.error.BusinessException;
import com.hiretrack.common.error.ErrorCode;
import com.hiretrack.interview.InterviewRepository;
import com.hiretrack.interview.domain.Interview;
import com.hiretrack.interview.domain.InterviewType;
import com.hiretrack.notification.NotificationProperties;
import com.hiretrack.posting.JobPostingRepository;
import com.hiretrack.posting.domain.ApplicationStatus;
import com.hiretrack.posting.domain.JobPosting;
import com.hiretrack.user.UserRepository;

/**
 * iCal 구독 피드. 구글·애플·아웃룩 캘린더에서 URL 로 구독하면 마감일과 면접 일정이 보인다.
 * 이벤트마다 고정 UID 를 써서 캘린더 앱이 수정·삭제를 같은 일정으로 인식하게 한다.
 */
@Service
@Transactional(readOnly = true)
public class CalendarService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String UID_DOMAIN = "@hiretrack";
    private static final Map<InterviewType, String> TYPE_LABEL = Map.of(
            InterviewType.FIRST, "1차 면접", InterviewType.SECOND, "2차 면접", InterviewType.EXECUTIVE, "임원 면접",
            InterviewType.CODING_TEST, "코딩테스트", InterviewType.ASSIGNMENT, "과제");

    private final CalendarFeedRepository feedRepository;
    private final JobPostingRepository postingRepository;
    private final InterviewRepository interviewRepository;
    private final UserRepository userRepository;
    private final NotificationProperties properties;
    private final Clock clock;

    public CalendarService(CalendarFeedRepository feedRepository, JobPostingRepository postingRepository,
                           InterviewRepository interviewRepository, UserRepository userRepository,
                           NotificationProperties properties, Clock clock) {
        this.feedRepository = feedRepository;
        this.postingRepository = postingRepository;
        this.interviewRepository = interviewRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** 처음 조회할 때 토큰을 발급한다. 설정 화면을 여러 탭에서 동시에 열어도 하나만 만들어지도록 사용자 행을 잠근다 */
    @Transactional
    public CalendarSubscription subscription(Long userId) {
        lockUser(userId);
        CalendarFeed feed = feedRepository.findById(userId)
                .orElseGet(() -> feedRepository.save(new CalendarFeed(userId, newToken(), clock.instant())));
        return toSubscription(feed);
    }

    /** 유출 대비: 새 토큰을 발급하면 이전 URL 은 바로 404 가 된다 */
    @Transactional
    public CalendarSubscription regenerate(Long userId) {
        lockUser(userId);
        CalendarFeed feed = feedRepository.findById(userId).orElse(null);
        if (feed == null) {
            feed = feedRepository.save(new CalendarFeed(userId, newToken(), clock.instant()));
        } else {
            feed.rotate(newToken(), clock.instant());
        }
        return toSubscription(feed);
    }

    public String feed(String token) {
        CalendarFeed feed = feedRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.CALENDAR_NOT_FOUND));
        Long userId = feed.getUserId();
        Instant now = clock.instant();
        String app = properties.baseUrl();

        IcsWriter ics = new IcsWriter().beginCalendar("HireTrack 지원 일정");

        // 지원 전(관심) 공고의 마감일 — 종일 일정
        for (JobPosting p : postingRepository.search(userId, ApplicationStatus.INTERESTED, null, null, null)) {
            if (p.getDeadline() == null) continue;
            ics.allDayEvent("posting-" + p.getId() + "-deadline" + UID_DOMAIN, p.getDeadline(),
                    "[마감] " + p.getCompany() + " · " + p.getTitle(),
                    "공고 보기: " + app + "/postings/" + p.getId(), now, p.getUpdatedAt());
        }

        // 면접 — 1시간 전 알림, 설명에 제출본 자소서 링크
        for (Interview i : interviewRepository.findAllByUserId(userId)) {
            JobPosting p = i.getPosting();
            StringBuilder description = new StringBuilder();
            if (i.getMemo() != null) description.append(i.getMemo()).append("\n\n");
            description.append("제출한 자기소개서: ").append(app).append("/postings/").append(p.getId()).append("?tab=essay");
            ics.timedEvent("interview-" + i.getId() + UID_DOMAIN, i.getStartsAt(), i.getEndsAt(),
                    "[" + TYPE_LABEL.get(i.getType()) + "] " + p.getCompany() + " · " + p.getTitle(),
                    i.getLocation(), description.toString(), 60, now, i.getUpdatedAt());
        }
        return ics.endCalendar().toString();
    }

    private void lockUser(Long userId) {
        userRepository.findForUpdateById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private CalendarSubscription toSubscription(CalendarFeed feed) {
        return new CalendarSubscription(properties.baseUrl() + "/api/calendar/" + feed.getToken() + ".ics",
                TimeConfig.toOffset(feed.getCreatedAt()));
    }

    /** 256비트 난수 → URL 에 그대로 쓸 수 있는 base64url (43자) */
    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
