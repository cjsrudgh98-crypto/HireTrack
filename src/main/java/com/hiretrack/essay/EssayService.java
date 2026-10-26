package com.hiretrack.essay;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hiretrack.common.error.BusinessException;
import com.hiretrack.common.error.ErrorCode;
import com.hiretrack.essay.domain.EssayAnswer;
import com.hiretrack.essay.domain.EssayCategory;
import com.hiretrack.essay.domain.EssayDraft;
import com.hiretrack.essay.domain.EssayQuestion;
import com.hiretrack.essay.domain.Submission;
import com.hiretrack.essay.dto.EssayDtos.AnswerRequest;
import com.hiretrack.essay.dto.EssayDtos.AnswerResponse;
import com.hiretrack.essay.dto.EssayDtos.AnswerSearchResult;
import com.hiretrack.essay.dto.EssayDtos.DraftResponse;
import com.hiretrack.essay.dto.EssayDtos.QuestionRequest;
import com.hiretrack.essay.dto.EssayDtos.QuestionResponse;
import com.hiretrack.posting.PostingService;
import com.hiretrack.posting.domain.JobPosting;

@Service
@Transactional(readOnly = true)
public class EssayService {

    private static final int SEARCH_LIMIT = 50;

    private final EssayQuestionRepository questionRepository;
    private final EssayAnswerRepository answerRepository;
    private final EssayDraftRepository draftRepository;
    private final AnswerTemplateRepository templateRepository;
    private final PostingService postingService;
    private final Clock clock;

    public EssayService(EssayQuestionRepository questionRepository, EssayAnswerRepository answerRepository,
                        EssayDraftRepository draftRepository, AnswerTemplateRepository templateRepository,
                        PostingService postingService, Clock clock) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.draftRepository = draftRepository;
        this.templateRepository = templateRepository;
        this.postingService = postingService;
        this.clock = clock;
    }

    // ---------- 문항 ----------

    /** 공고의 문항 + 각 문항의 최신 답변·제출본·임시저장 */
    public List<QuestionResponse> questions(Long userId, Long postingId) {
        postingService.getOwnedPosting(userId, postingId);
        List<EssayQuestion> questions = questionRepository.findByPostingIdOrderByDisplayOrderAscIdAsc(postingId);
        if (questions.isEmpty()) return List.of();

        List<Long> ids = questions.stream().map(EssayQuestion::getId).toList();
        Map<Long, EssayAnswer> latest = byQuestion(answerRepository.findLatestByQuestionIds(ids));
        Map<Long, EssayAnswer> submitted = byQuestion(answerRepository.findByQuestionIdInAndSubmittedTrue(ids));
        Map<Long, EssayDraft> drafts = draftRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(EssayDraft::getQuestionId, Function.identity()));

        return questions.stream()
                .map(q -> QuestionResponse.of(q, latest.get(q.getId()), submitted.get(q.getId()), drafts.get(q.getId())))
                .toList();
    }

    @Transactional
    public QuestionResponse addQuestion(Long userId, Long postingId, QuestionRequest request) {
        JobPosting posting = postingService.getOwnedPosting(userId, postingId);
        int order = questionRepository.maxDisplayOrder(postingId) + 1;
        EssayQuestion question = questionRepository.save(new EssayQuestion(posting, request.question().trim(),
                request.maxLength(), request.category(), order, clock.instant()));
        return QuestionResponse.of(question, null, null, null);
    }

    @Transactional
    public QuestionResponse updateQuestion(Long userId, Long questionId, QuestionRequest request) {
        EssayQuestion question = getOwnedQuestion(userId, questionId);
        requireNotLocked(questionId);
        question.update(request.question().trim(), request.maxLength(), request.category());
        EssayAnswer latest = answerRepository.findByQuestionIdOrderByVersionDesc(questionId).stream().findFirst().orElse(null);
        return QuestionResponse.of(question, latest, null, draftRepository.findById(questionId).orElse(null));
    }

    @Transactional
    public void deleteQuestion(Long userId, Long questionId) {
        EssayQuestion question = getOwnedQuestion(userId, questionId);
        requireNotLocked(questionId);
        answerRepository.deleteByQuestionId(questionId);
        draftRepository.findById(questionId).ifPresent(draftRepository::delete);
        questionRepository.delete(question);
    }

    // ---------- 답변 버전 ----------

    public List<AnswerResponse> versions(Long userId, Long questionId) {
        getOwnedQuestion(userId, questionId);
        return answerRepository.findByQuestionIdOrderByVersionDesc(questionId).stream().map(AnswerResponse::from).toList();
    }

    public AnswerResponse version(Long userId, Long questionId, int version) {
        getOwnedQuestion(userId, questionId);
        return answerRepository.findByQuestionIdAndVersion(questionId, version)
                .map(AnswerResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANSWER_NOT_FOUND));
    }

    /**
     * 새 버전 저장(append-only). 동시성은 두 겹으로 막는다.
     * <ol>
     *   <li>baseVersion 검사: 클라이언트가 본 최신 버전과 서버 최신 버전이 다르면 409 — 다른 탭에서 먼저 저장한 경우</li>
     *   <li>(question_id, version) 유니크 제약: 두 요청이 같은 순간에 검사를 통과해도 DB 가 하나만 받아들이고 나머지는 409</li>
     * </ol>
     * 자동 재시도 대신 409 를 돌려주는 이유: 재시도하면 다른 탭에서 쓴 내용을 모른 채 덮어쓴 "새 버전"이 생기기 때문.
     */
    @Transactional
    public AnswerResponse saveVersion(Long userId, Long questionId, AnswerRequest request) {
        EssayQuestion question = getOwnedQuestion(userId, questionId);
        List<EssayAnswer> existing = answerRepository.findByQuestionIdOrderByVersionDesc(questionId);
        if (existing.stream().anyMatch(EssayAnswer::isSubmitted)) {
            throw new BusinessException(ErrorCode.ANSWER_LOCKED);
        }
        Integer latest = existing.isEmpty() ? null : existing.getFirst().getVersion();
        if (!Objects.equals(latest, request.baseVersion())) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        if (request.sourceTemplateId() != null) {
            templateRepository.findById(request.sourceTemplateId())
                    .filter(t -> t.isOwnedBy(userId))
                    .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND));
        }

        // 템플릿에서 시작한 답변을 고쳐 쓴 버전도 같은 템플릿 출신으로 본다(새 템플릿을 가져오면 그걸로 바뀜).
        // 그래야 제출본(보통 여러 번 고친 마지막 버전)의 출처로 "템플릿별 서류 통과율"을 낼 수 있다.
        Long sourceTemplateId = request.sourceTemplateId() != null ? request.sourceTemplateId()
                : existing.isEmpty() ? null : existing.getFirst().getSourceTemplateId();
        EssayAnswer answer = new EssayAnswer(question, latest == null ? 1 : latest + 1, request.content(),
                sourceTemplateId, clock.instant());
        try {
            answerRepository.saveAndFlush(answer);
        } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        // 저장된 내용이 곧 최신본이므로 임시저장은 비운다
        draftRepository.findById(questionId).ifPresent(draftRepository::delete);
        return AnswerResponse.from(answer);
    }

    /**
     * 이미 지원한 공고에 나중에 문항을 추가한 경우처럼 자동 고정을 못 탄 문항을 직접 제출본으로 지정한다.
     */
    @Transactional
    public AnswerResponse submit(Long userId, Long questionId, int version) {
        EssayQuestion question = getOwnedQuestion(userId, questionId);
        boolean applied = question.getPosting().getApplication().getAppliedAt() != null;
        List<EssayAnswer> existing = answerRepository.findByQuestionIdOrderByVersionDesc(questionId);
        if (!applied || existing.stream().anyMatch(EssayAnswer::isSubmitted)) {
            throw new BusinessException(ErrorCode.SUBMIT_NOT_ALLOWED);
        }
        EssayAnswer answer = existing.stream().filter(a -> a.getVersion() == version).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.ANSWER_NOT_FOUND));
        Submission.freeze(answer);
        return AnswerResponse.from(answer);
    }

    // ---------- 임시저장 ----------

    @Transactional
    public DraftResponse saveDraft(Long userId, Long questionId, String content) {
        getOwnedQuestion(userId, questionId);
        questionRepository.findForUpdateById(questionId);
        requireNotLocked(questionId);
        EssayDraft draft = draftRepository.findById(questionId).orElse(null);
        if (draft == null) {
            draft = draftRepository.save(new EssayDraft(questionId, content, clock.instant()));
        } else {
            draft.update(content, clock.instant());
        }
        return DraftResponse.from(draft);
    }

    // ---------- 검색 ----------

    public List<AnswerSearchResult> search(Long userId, String keyword, EssayCategory category, Long excludeQuestionId) {
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return answerRepository.search(userId, normalized, category, excludeQuestionId, PageRequest.of(0, SEARCH_LIMIT))
                .stream().map(AnswerSearchResult::from).toList();
    }

    // ---------- 내부 ----------

    /** 문항 권한 체크: 문항이 속한 공고의 주인만. 없으면 404, 남의 문항이면 403. */
    private EssayQuestion getOwnedQuestion(Long userId, Long questionId) {
        EssayQuestion question = questionRepository.findWithPostingById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND));
        if (!question.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return question;
    }

    private void requireNotLocked(Long questionId) {
        if (!answerRepository.findByQuestionIdInAndSubmittedTrue(List.of(questionId)).isEmpty()) {
            throw new BusinessException(ErrorCode.ANSWER_LOCKED);
        }
    }

    private static Map<Long, EssayAnswer> byQuestion(List<EssayAnswer> answers) {
        return answers.stream().collect(Collectors.toMap(a -> a.getQuestion().getId(), Function.identity(), (a, b) -> a));
    }
}
