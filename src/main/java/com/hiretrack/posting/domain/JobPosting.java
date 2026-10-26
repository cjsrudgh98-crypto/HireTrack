package com.hiretrack.posting.domain;

import java.time.Instant;
import java.time.LocalDate;

import com.hiretrack.user.User;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "job_posting", indexes = @Index(name = "idx_posting_user", columnList = "user_id"))
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String company;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String url;

    @Column(length = 100)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EmploymentType employmentType;

    private LocalDate deadline;

    @Column(length = 5000)
    private String requirements;

    /** MVP 에서는 공고와 지원을 1:1 로 본다. 공고를 만들면 지원 레코드도 함께 생긴다. */
    @OneToOne(mappedBy = "posting", cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    private Application application;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected JobPosting() {
    }

    public JobPosting(User user, PostingContent content, String memo, Instant now) {
        this.user = user;
        apply(content);
        this.createdAt = now;
        this.updatedAt = now;
        this.application = new Application(this, memo, now);
    }

    public void update(PostingContent content, String memo, Instant now) {
        apply(content);
        application.updateMemo(memo, now);
        this.updatedAt = now;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    private void apply(PostingContent content) {
        this.company = content.company();
        this.title = content.title();
        this.url = content.url();
        this.location = content.location();
        this.employmentType = content.employmentType();
        this.deadline = content.deadline();
        this.requirements = content.requirements();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getCompany() {
        return company;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getLocation() {
        return location;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public String getRequirements() {
        return requirements;
    }

    public Application getApplication() {
        return application;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
