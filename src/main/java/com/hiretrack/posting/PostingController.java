package com.hiretrack.posting;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hiretrack.posting.domain.ApplicationStatus;
import com.hiretrack.posting.domain.EmploymentType;
import com.hiretrack.posting.dto.MemoRequest;
import com.hiretrack.posting.dto.PostingDetailResponse;
import com.hiretrack.posting.dto.PostingRequest;
import com.hiretrack.posting.dto.PostingSummaryResponse;
import com.hiretrack.posting.dto.StatusChangeRequest;
import com.hiretrack.posting.dto.StatusHistoryResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/postings")
public class PostingController {

    private final PostingService postingService;

    public PostingController(PostingService postingService) {
        this.postingService = postingService;
    }

    @GetMapping
    public List<PostingSummaryResponse> list(@AuthenticationPrincipal Long userId,
                                             @RequestParam(required = false) ApplicationStatus status,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) EmploymentType employmentType,
                                             @RequestParam(required = false) String location) {
        return postingService.search(userId, status, keyword, employmentType, location);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostingDetailResponse create(@AuthenticationPrincipal Long userId,
                                        @Valid @RequestBody PostingRequest request) {
        return postingService.create(userId, request);
    }

    @GetMapping("/{id}")
    public PostingDetailResponse get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return postingService.get(userId, id);
    }

    @PutMapping("/{id}")
    public PostingDetailResponse update(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                        @Valid @RequestBody PostingRequest request) {
        return postingService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        postingService.delete(userId, id);
    }

    @PatchMapping("/{id}/status")
    public PostingDetailResponse changeStatus(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                              @Valid @RequestBody StatusChangeRequest request) {
        return postingService.changeStatus(userId, id, request.status());
    }

    @PatchMapping("/{id}/memo")
    public PostingDetailResponse updateMemo(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                            @Valid @RequestBody MemoRequest request) {
        return postingService.updateMemo(userId, id, request.memo());
    }

    @GetMapping("/{id}/history")
    public List<StatusHistoryResponse> history(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return postingService.history(userId, id);
    }
}
