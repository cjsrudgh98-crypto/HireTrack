package com.hiretrack.essay.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 작성 중 자동 임시저장. 문항당 1행을 덮어쓴다.
 * 버전 테이블과 분리해서 버전 목록에는 사용자가 [버전 저장]을 누른 것만 쌓이게 한다.
 */
@Entity
@Table(name = "essay_drafts")
public class EssayDraft {

    @Id
    private Long questionId;

    @Column(nullable = false, length = 20000)
    private String content;

    @Column(nullable = false)
    private Instant updatedAt;

    protected EssayDraft() {
    }

    public EssayDraft(Long questionId, String content, Instant updatedAt) {
        this.questionId = questionId;
        this.content = content;
        this.updatedAt = updatedAt;
    }

    public void update(String content, Instant updatedAt) {
        this.content = content;
        this.updatedAt = updatedAt;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public String getContent() {
        return content;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
