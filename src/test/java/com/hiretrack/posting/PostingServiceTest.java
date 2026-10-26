package com.hiretrack.posting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.hiretrack.common.error.BusinessException;
import com.hiretrack.common.error.ErrorCode;
import com.hiretrack.notification.NotificationLogRepository;
import com.hiretrack.posting.domain.ApplicationStatus;
import com.hiretrack.posting.domain.EmploymentType;
import com.hiretrack.posting.domain.JobPosting;
import com.hiretrack.posting.domain.PostingContent;
import com.hiretrack.posting.dto.PostingDetailResponse;
import com.hiretrack.posting.dto.PostingRequest;
import com.hiretrack.user.User;
import com.hiretrack.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class PostingServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long POSTING_ID = 10L;
    private static final Instant CREATED = Instant.parse("2026-10-20T01:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-26T07:00:00Z");

    @Mock
    private JobPostingRepository postingRepository;
    @Mock
    private StatusHistoryRepository historyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationLogRepository notificationLogRepository;
    @Mock
    private ApplicationEventPublisher events;

    private PostingService postingService;
    private JobPosting posting;

    @BeforeEach
    void setUp() {
        postingService = new PostingService(postingRepository, historyRepository, userRepository,
                notificationLogRepository, events, Clock.fixed(NOW, ZoneOffset.UTC));

        User owner = new User("owner@test.com", "pw", CREATED);
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);
        posting = new JobPosting(owner,
                new PostingContent("○○테크", "백엔드 개발자", null, null, EmploymentType.FULL_TIME, null, null),
                null, CREATED);
        ReflectionTestUtils.setField(posting, "id", POSTING_ID);
    }

    @Test
    void 공고를_등록하면_관심_상태의_지원_레코드가_함께_만들어진다() {
        given(userRepository.getReferenceById(OWNER_ID)).willReturn(posting.getUser());
        given(postingRepository.save(any(JobPosting.class))).willAnswer(inv -> inv.getArgument(0));

        PostingDetailResponse response = postingService.create(OWNER_ID, new PostingRequest(
                "△△소프트", "풀스택", "", "서울", EmploymentType.FULL_TIME, null, null, "첫 메모"));

        assertThat(response.status()).isEqualTo(ApplicationStatus.INTERESTED);
        assertThat(response.memo()).isEqualTo("첫 메모");
        assertThat(response.url()).isNull();
        assertThat(response.appliedAt()).isNull();
    }

    @Test
    void 본인_공고는_상태를_변경하고_지원일이_기록된다() {
        given(postingRepository.findWithApplicationForUpdateById(POSTING_ID)).willReturn(Optional.of(posting));

        PostingDetailResponse response = postingService.changeStatus(OWNER_ID, POSTING_ID, ApplicationStatus.APPLIED);

        assertThat(response.status()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(response.appliedAt().toInstant()).isEqualTo(NOW);
        assertThat(posting.getApplication().getHistories()).hasSize(2);
        verify(events).publishEvent(new PostingEvents.FirstApplied(POSTING_ID)); // 자소서 제출본 고정 트리거
    }

    @Test
    void 다른_사용자의_공고_상태를_바꾸면_FORBIDDEN() {
        given(postingRepository.findWithApplicationForUpdateById(POSTING_ID)).willReturn(Optional.of(posting));

        assertThatThrownBy(() -> postingService.changeStatus(OTHER_ID, POSTING_ID, ApplicationStatus.APPLIED))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(posting.getApplication().getStatus()).isEqualTo(ApplicationStatus.INTERESTED);
        assertThat(posting.getApplication().getHistories()).hasSize(1);
    }

    @Test
    void 다른_사용자의_공고는_조회_수정_메모_이력_모두_FORBIDDEN() {
        given(postingRepository.findWithApplicationById(POSTING_ID)).willReturn(Optional.of(posting));
        PostingRequest request = new PostingRequest("x", "y", null, null, null, null, null, null);

        assertForbidden(() -> postingService.get(OTHER_ID, POSTING_ID));
        assertForbidden(() -> postingService.update(OTHER_ID, POSTING_ID, request));
        assertForbidden(() -> postingService.updateMemo(OTHER_ID, POSTING_ID, "memo"));
        assertForbidden(() -> postingService.history(OTHER_ID, POSTING_ID));
        assertThat(posting.getCompany()).isEqualTo("○○테크");
    }

    @Test
    void 다른_사용자의_공고는_삭제되지_않는다() {
        given(postingRepository.findWithApplicationById(POSTING_ID)).willReturn(Optional.of(posting));

        assertForbidden(() -> postingService.delete(OTHER_ID, POSTING_ID));
        verify(postingRepository, never()).delete(any());
    }

    @Test
    void 없는_공고는_POSTING_NOT_FOUND() {
        given(postingRepository.findWithApplicationById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postingService.get(OWNER_ID, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POSTING_NOT_FOUND);
    }

    private static void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}
