package com.hiretrack.essay.domain;

import java.time.Instant;

import com.hiretrack.user.User;

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

/** 답변 라이브러리. 잘 쓴 답변을 유형별로 모아두고 새 문항에 가져다 쓴다. */
@Entity
@Table(name = "answer_templates", indexes = @Index(name = "idx_template_user_category", columnList = "user_id, category"))
public class AnswerTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EssayCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 20000)
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected AnswerTemplate() {
    }

    public AnswerTemplate(User user, EssayCategory category, String title, String content, Instant now) {
        this.user = user;
        this.category = category;
        this.title = title;
        this.content = content;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(EssayCategory category, String title, String content, Instant now) {
        this.category = category;
        this.title = title;
        this.content = content;
        this.updatedAt = now;
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    public Long getId() {
        return id;
    }

    public EssayCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
