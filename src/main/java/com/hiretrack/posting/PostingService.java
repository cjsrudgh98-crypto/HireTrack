package com.hiretrack.posting;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hiretrack.common.error.BusinessException;
import com.hiretrack.common.error.ErrorCode;
import com.hiretrack.notification.NotificationLogRepository;
import com.hiretrack.posting.domain.ApplicationStatus;
import com.hiretrack.posting.domain.EmploymentType;
import com.hiretrack.posting.domain.JobPosting;
import com.hiretrack.posting.domain.StatusChange;
import com.hiretrack.posting.dto.PostingDetailResponse;
import com.hiretrack.posting.dto.PostingRequest;
import com.hiretrack.posting.dto.PostingSummaryResponse;
import com.hiretrack.posting.dto.StatusHistoryResponse;
import com.hiretrack.user.UserRepository;

@Service
@Transactional(readOnly = true)
public class PostingService {

    private final JobPostingRepository postingRepository;
    private final StatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PostingService(JobPostingRepository postingRepository, StatusHistoryRepository historyRepository,
                          UserRepository userRepository, NotificationLogRepository notificationLogRepository,
                          ApplicationEventPublisher events, Clock clock) {
        this.postingRepository = postingRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.events = events;
        this.clock = clock;
    }

    public List<PostingSummaryResponse> search(Long userId, ApplicationStatus status, String keyword,
                                               EmploymentType employmentType, String location) {
        return postingRepository.search(userId, status, blankToNull(keyword), employmentType, blankToNull(location))
                .stream()
                .map(PostingSummaryResponse::from)
                .toList();
    }

    /** 공고 등록 시 지원 레코드가 '관심' 상태로 함께 생성된다. */
    @Transactional
    public PostingDetailResponse create(Long userId, PostingRequest request) {
        var user = userRepository.getReferenceById(userId);
        JobPosting posting = new JobPosting(user, request.toContent(), request.memo(), clock.instant());
        return PostingDetailResponse.from(postingRepository.save(posting));
    }

    public PostingDetailResponse get(Long userId, Long postingId) {
        return PostingDetailResponse.from(getOwnedPosting(userId, postingId));
    }

    @Transactional
    public PostingDetailResponse update(Long userId, Long postingId, PostingRequest request) {
        JobPosting posting = getOwnedPosting(userId, postingId);
        posting.update(request.toContent(), request.memo(), clock.instant());
        return PostingDetailResponse.from(posting);
    }

    @Transactional
    public void delete(Long userId, Long postingId) {
        JobPosting posting = getOwnedPosting(userId, postingId);
        events.publishEvent(new PostingEvents.Deleting(posting.getId()));
        notificationLogRepository.deleteByPostingId(posting.getId());
        postingRepository.delete(posting);
    }

    /**
     * 상태 변경과 이력 저장은 같은 트랜잭션 안에서 처리된다(이력은 cascade 로 함께 저장).
     * 첫 지원이면 자소서 제출본 고정 이벤트도 같은 트랜잭션에서 처리된다.
     */
    @Transactional
    public PostingDetailResponse changeStatus(Long userId, Long postingId, ApplicationStatus status) {
        JobPosting posting = checkOwner(userId, postingRepository.findWithApplicationForUpdateById(postingId));
        StatusChange change = posting.getApplication().changeStatus(status, clock.instant());
        if (change.firstApplied()) events.publishEvent(new PostingEvents.FirstApplied(posting.getId()));
        if (change.applyReverted()) events.publishEvent(new PostingEvents.ApplyReverted(posting.getId()));
        return PostingDetailResponse.from(posting);
    }

    @Transactional
    public PostingDetailResponse updateMemo(Long userId, Long postingId, String memo) {
        JobPosting posting = getOwnedPosting(userId, postingId);
        posting.getApplication().updateMemo(memo, clock.instant());
        return PostingDetailResponse.from(posting);
    }

    public List<StatusHistoryResponse> history(Long userId, Long postingId) {
        JobPosting posting = getOwnedPosting(userId, postingId);
        return historyRepository.findByApplicationIdOrderByChangedAtAscIdAsc(posting.getApplication().getId())
                .stream()
                .map(StatusHistoryResponse::from)
                .toList();
    }

    /** 모든 공고 API(자소서 포함)의 권한 체크 지점. 없으면 404, 남의 공고면 403. */
    public JobPosting getOwnedPosting(Long userId, Long postingId) {
        return checkOwner(userId, postingRepository.findWithApplicationById(postingId));
    }

    private static JobPosting checkOwner(Long userId, Optional<JobPosting> found) {
        JobPosting posting = found
                .orElseThrow(() -> new BusinessException(ErrorCode.POSTING_NOT_FOUND));
        if (!posting.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return posting;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
