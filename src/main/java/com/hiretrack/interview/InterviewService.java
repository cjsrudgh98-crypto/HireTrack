package com.hiretrack.interview;

import java.time.Clock;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hiretrack.common.error.BusinessException;
import com.hiretrack.common.error.ErrorCode;
import com.hiretrack.interview.domain.Interview;
import com.hiretrack.interview.dto.InterviewDtos.InterviewRequest;
import com.hiretrack.interview.dto.InterviewDtos.InterviewResponse;
import com.hiretrack.posting.PostingEvents;
import com.hiretrack.posting.PostingService;
import com.hiretrack.posting.domain.JobPosting;

@Service
@Transactional(readOnly = true)
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final PostingService postingService;
    private final Clock clock;

    public InterviewService(InterviewRepository interviewRepository, PostingService postingService, Clock clock) {
        this.interviewRepository = interviewRepository;
        this.postingService = postingService;
        this.clock = clock;
    }

    public List<InterviewResponse> list(Long userId, Long postingId) {
        postingService.getOwnedPosting(userId, postingId);
        return interviewRepository.findByPostingIdOrderByStartsAtAsc(postingId).stream()
                .map(InterviewResponse::from).toList();
    }

    /** 지금부터 시작하는 면접. 진행 중인 면접도 보이도록 3시간 전부터 포함한다. */
    public List<InterviewResponse> upcoming(Long userId) {
        return interviewRepository.findUpcoming(userId, clock.instant().minusSeconds(3 * 3600)).stream()
                .map(InterviewResponse::from).toList();
    }

    @Transactional
    public InterviewResponse create(Long userId, Long postingId, InterviewRequest request) {
        JobPosting posting = postingService.getOwnedPosting(userId, postingId);
        Interview interview = interviewRepository.save(new Interview(posting, request.type(), request.startsAt().toInstant(),
                request.durationOrDefault(), blankToNull(request.location()), blankToNull(request.memo()), clock.instant()));
        return InterviewResponse.from(interview);
    }

    @Transactional
    public InterviewResponse update(Long userId, Long interviewId, InterviewRequest request) {
        Interview interview = getOwned(userId, interviewId);
        interview.update(request.type(), request.startsAt().toInstant(), request.durationOrDefault(),
                blankToNull(request.location()), blankToNull(request.memo()), clock.instant());
        return InterviewResponse.from(interview);
    }

    @Transactional
    public void delete(Long userId, Long interviewId) {
        interviewRepository.delete(getOwned(userId, interviewId));
    }

    @EventListener
    public void onPostingDeleting(PostingEvents.Deleting event) {
        interviewRepository.deleteByPostingId(event.postingId());
    }

    private Interview getOwned(Long userId, Long interviewId) {
        Interview interview = interviewRepository.findWithPostingById(interviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_NOT_FOUND));
        if (!interview.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return interview;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
