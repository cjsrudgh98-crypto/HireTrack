package com.hiretrack.essay.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 답변 버전. <b>append-only</b> — 내용은 한 번 저장하면 바뀌지 않고, 고칠 때마다 새 버전 행이 쌓인다.
 * 바뀔 수 있는 건 제출본 표시(submitted)뿐이다.
 * <p>
 * (question_id, version) 유니크 제약이 두 탭에서 동시에 저장할 때 같은 번호가 생기는 것을 막는다.
 */
@Entity
@Table(name = "essay_answers",
        uniqueConstraints = @UniqueConstraint(name = "uk_answer_question_version", columnNames = {"question_id", "version"}))
public class EssayAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, updatable = false)
    private EssayQuestion question;

    @Column(nullable = false, updatable = false)
    private int version;

    @Column(nullable = false, updatable = false, length = 20000)
    private String content;

    /** 공백 포함 글자 수 */
    @Column(nullable = false, updatable = false)
    private int charCount;

    @Column(nullable = false)
    private boolean submitted;

    /** 템플릿에서 가져와 쓴 답변이면 그 템플릿 ID. 템플릿이 지워져도 출처 기록은 남도록 FK 를 두지 않는다. */
    @Column(updatable = false)
    private Long sourceTemplateId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected EssayAnswer() {
    }

    public EssayAnswer(EssayQuestion question, int version, String content, Long sourceTemplateId, Instant createdAt) {
        this.question = question;
        this.version = version;
        this.content = content;
        this.charCount = content.codePointCount(0, content.length());
        this.sourceTemplateId = sourceTemplateId;
        this.createdAt = createdAt;
    }

    void markSubmitted() {
        this.submitted = true;
    }

    void unmarkSubmitted() {
        this.submitted = false;
    }

    public Long getId() {
        return id;
    }

    public EssayQuestion getQuestion() {
        return question;
    }

    public int getVersion() {
        return version;
    }

    public String getContent() {
        return content;
    }

    public int getCharCount() {
        return charCount;
    }

    public boolean isSubmitted() {
        return submitted;
    }

    public Long getSourceTemplateId() {
        return sourceTemplateId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
