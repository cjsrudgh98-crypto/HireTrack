package com.hiretrack.essay;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hiretrack.essay.domain.EssayCategory;
import com.hiretrack.essay.dto.EssayDtos.AnswerRequest;
import com.hiretrack.essay.dto.EssayDtos.AnswerResponse;
import com.hiretrack.essay.dto.EssayDtos.AnswerSearchResult;
import com.hiretrack.essay.dto.EssayDtos.DraftRequest;
import com.hiretrack.essay.dto.EssayDtos.DraftResponse;
import com.hiretrack.essay.dto.EssayDtos.QuestionRequest;
import com.hiretrack.essay.dto.EssayDtos.QuestionResponse;

import jakarta.validation.Valid;

@RestController
public class EssayController {

    private final EssayService essayService;

    public EssayController(EssayService essayService) {
        this.essayService = essayService;
    }

    @GetMapping("/api/postings/{postingId}/questions")
    public List<QuestionResponse> questions(@AuthenticationPrincipal Long userId, @PathVariable Long postingId) {
        return essayService.questions(userId, postingId);
    }

    @PostMapping("/api/postings/{postingId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionResponse addQuestion(@AuthenticationPrincipal Long userId, @PathVariable Long postingId,
                                        @Valid @RequestBody QuestionRequest request) {
        return essayService.addQuestion(userId, postingId, request);
    }

    @PutMapping("/api/questions/{questionId}")
    public QuestionResponse updateQuestion(@AuthenticationPrincipal Long userId, @PathVariable Long questionId,
                                           @Valid @RequestBody QuestionRequest request) {
        return essayService.updateQuestion(userId, questionId, request);
    }

    @DeleteMapping("/api/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuestion(@AuthenticationPrincipal Long userId, @PathVariable Long questionId) {
        essayService.deleteQuestion(userId, questionId);
    }

    @GetMapping("/api/questions/{questionId}/answers")
    public List<AnswerResponse> versions(@AuthenticationPrincipal Long userId, @PathVariable Long questionId) {
        return essayService.versions(userId, questionId);
    }

    @PostMapping("/api/questions/{questionId}/answers")
    @ResponseStatus(HttpStatus.CREATED)
    public AnswerResponse saveVersion(@AuthenticationPrincipal Long userId, @PathVariable Long questionId,
                                      @Valid @RequestBody AnswerRequest request) {
        return essayService.saveVersion(userId, questionId, request);
    }

    @GetMapping("/api/questions/{questionId}/answers/{version}")
    public AnswerResponse version(@AuthenticationPrincipal Long userId, @PathVariable Long questionId,
                                  @PathVariable int version) {
        return essayService.version(userId, questionId, version);
    }

    @PostMapping("/api/questions/{questionId}/answers/{version}/submit")
    public AnswerResponse submit(@AuthenticationPrincipal Long userId, @PathVariable Long questionId,
                                 @PathVariable int version) {
        return essayService.submit(userId, questionId, version);
    }

    @PutMapping("/api/questions/{questionId}/draft")
    public DraftResponse saveDraft(@AuthenticationPrincipal Long userId, @PathVariable Long questionId,
                                   @Valid @RequestBody DraftRequest request) {
        return essayService.saveDraft(userId, questionId, request.content());
    }

    @GetMapping("/api/answers/search")
    public List<AnswerSearchResult> search(@AuthenticationPrincipal Long userId,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) EssayCategory category,
                                           @RequestParam(required = false) Long excludeQuestionId) {
        return essayService.search(userId, keyword, category, excludeQuestionId);
    }
}
