package com.hiretrack.interview;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hiretrack.interview.dto.InterviewDtos.InterviewRequest;
import com.hiretrack.interview.dto.InterviewDtos.InterviewResponse;

import jakarta.validation.Valid;

@RestController
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @GetMapping("/api/postings/{postingId}/interviews")
    public List<InterviewResponse> list(@AuthenticationPrincipal Long userId, @PathVariable Long postingId) {
        return interviewService.list(userId, postingId);
    }

    @PostMapping("/api/postings/{postingId}/interviews")
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewResponse create(@AuthenticationPrincipal Long userId, @PathVariable Long postingId,
                                    @Valid @RequestBody InterviewRequest request) {
        return interviewService.create(userId, postingId, request);
    }

    @PutMapping("/api/interviews/{interviewId}")
    public InterviewResponse update(@AuthenticationPrincipal Long userId, @PathVariable Long interviewId,
                                    @Valid @RequestBody InterviewRequest request) {
        return interviewService.update(userId, interviewId, request);
    }

    @DeleteMapping("/api/interviews/{interviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long interviewId) {
        interviewService.delete(userId, interviewId);
    }

    @GetMapping("/api/interviews/upcoming")
    public List<InterviewResponse> upcoming(@AuthenticationPrincipal Long userId) {
        return interviewService.upcoming(userId);
    }
}
