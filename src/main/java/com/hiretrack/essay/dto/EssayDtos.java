package com.hiretrack.essay.dto;

import java.time.OffsetDateTime;

import com.hiretrack.common.config.TimeConfig;
import com.hiretrack.essay.domain.AnswerTemplate;
import com.hiretrack.essay.domain.EssayAnswer;
import com.hiretrack.essay.domain.EssayCategory;
import com.hiretrack.essay.domain.EssayDraft;
import com.hiretrack.essay.domain.EssayQuestion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 자기소개서 API 요청·응답 */
public final class EssayDtos {

    private EssayDtos() {
    }

    public record QuestionRequest(
            @NotBlank(message = "문항 내용을 입력해 주세요.") @Size(max = 1000) String question,
            @Min(1) @Max(20000) Integer maxLength,
            @NotNull(message = "문항 유형을 골라 주세요.") EssayCategory category
    ) {
    }

    /**
     * @param latestAnswer     가장 최근 버전 (없으면 null)
     * @param submittedVersion 제출본 버전 (없으면 null) — 있으면 locked
     * @param draft            임시저장 (버전 저장 후에는 비워진다)
     */
    public record QuestionResponse(
            Long id, String question, Integer maxLength, EssayCategory category, int displayOrder,
            AnswerResponse latestAnswer, Integer submittedVersion, DraftResponse draft, boolean locked
    ) {
        public static QuestionResponse of(EssayQuestion q, EssayAnswer latest, EssayAnswer submitted, EssayDraft draft) {
            return new QuestionResponse(q.getId(), q.getQuestion(), q.getMaxLength(), q.getCategory(), q.getDisplayOrder(),
                    latest == null ? null : AnswerResponse.from(latest),
                    submitted == null ? null : submitted.getVersion(),
                    draft == null ? null : DraftResponse.from(draft),
                    submitted != null);
        }
    }

    /**
     * @param baseVersion 클라이언트가 보고 있던 최신 버전(없었으면 null). 서버의 최신 버전과 다르면 409.
     */
    public record AnswerRequest(
            @NotNull(message = "답변 내용을 입력해 주세요.") @Size(max = 20000) String content,
            Integer baseVersion,
            Long sourceTemplateId
    ) {
    }

    public record AnswerResponse(
            Long id, Long questionId, int version, String content, int charCount, boolean submitted,
            Long sourceTemplateId, OffsetDateTime createdAt
    ) {
        public static AnswerResponse from(EssayAnswer a) {
            return new AnswerResponse(a.getId(), a.getQuestion().getId(), a.getVersion(), a.getContent(), a.getCharCount(),
                    a.isSubmitted(), a.getSourceTemplateId(), TimeConfig.toOffset(a.getCreatedAt()));
        }
    }

    public record DraftRequest(@NotNull @Size(max = 20000) String content) {
    }

    public record DraftResponse(String content, OffsetDateTime updatedAt) {
        public static DraftResponse from(EssayDraft d) {
            return new DraftResponse(d.getContent(), TimeConfig.toOffset(d.getUpdatedAt()));
        }
    }

    public record TemplateRequest(
            @NotNull(message = "유형을 골라 주세요.") EssayCategory category,
            @NotBlank(message = "제목을 입력해 주세요.") @Size(max = 200) String title,
            @NotBlank(message = "내용을 입력해 주세요.") @Size(max = 20000) String content
    ) {
    }

    public record TemplateResponse(
            Long id, EssayCategory category, String title, String content, OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {
        public static TemplateResponse from(AnswerTemplate t) {
            return new TemplateResponse(t.getId(), t.getCategory(), t.getTitle(), t.getContent(),
                    TimeConfig.toOffset(t.getCreatedAt()), TimeConfig.toOffset(t.getUpdatedAt()));
        }
    }

    /** 과거 답변 검색 결과 — 어느 공고·문항에 쓴 답변인지 함께 */
    public record AnswerSearchResult(
            Long answerId, Long questionId, Long postingId, String company, String postingTitle,
            String question, EssayCategory category, int version, String content, int charCount,
            boolean submitted, OffsetDateTime createdAt
    ) {
        public static AnswerSearchResult from(EssayAnswer a) {
            EssayQuestion q = a.getQuestion();
            return new AnswerSearchResult(a.getId(), q.getId(), q.getPosting().getId(), q.getPosting().getCompany(),
                    q.getPosting().getTitle(), q.getQuestion(), q.getCategory(), a.getVersion(), a.getContent(),
                    a.getCharCount(), a.isSubmitted(), TimeConfig.toOffset(a.getCreatedAt()));
        }
    }
}
