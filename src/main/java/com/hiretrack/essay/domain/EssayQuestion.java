package com.hiretrack.essay.domain;

import java.time.Instant;

import com.hiretrack.posting.domain.JobPosting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "essay_questions", indexes = @Index(name = "idx_question_posting", columnList = "posting_id"))
public class EssayQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "posting_id", nullable = false)
    private JobPosting posting;

    @Column(nullable = false, length = 1000)
    private String question;

    /** 글자 수 제한(공백 포함 기준). 없으면 null */
    private Integer maxLength;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EssayCategory category;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected EssayQuestion() {
    }

    public EssayQuestion(JobPosting posting, String question, Integer maxLength, EssayCategory category,
                         int displayOrder, Instant createdAt) {
        this.posting = posting;
        this.question = question;
        this.maxLength = maxLength;
        this.category = category;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
    }

    public void update(String question, Integer maxLength, EssayCategory category) {
        this.question = question;
        this.maxLength = maxLength;
        this.category = category;
    }

    public boolean isOwnedBy(Long userId) {
        return posting.isOwnedBy(userId);
    }

    public Long getId() {
        return id;
    }

    public JobPosting getPosting() {
        return posting;
    }

    public String getQuestion() {
        return question;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public EssayCategory getCategory() {
        return category;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
