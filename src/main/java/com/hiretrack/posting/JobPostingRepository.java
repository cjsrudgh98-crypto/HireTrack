package com.hiretrack.posting;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hiretrack.posting.domain.ApplicationStatus;
import com.hiretrack.posting.domain.EmploymentType;
import com.hiretrack.posting.domain.JobPosting;

import jakarta.persistence.LockModeType;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    @Query("select p from JobPosting p join fetch p.application where p.id = :id")
    Optional<JobPosting> findWithApplicationById(@Param("id") Long id);

    /**
     * 상태 변경용. 같은 공고에 동시에 들어온 변경(빠른 연속 드래그, 웹·확장 동시 사용)을 한 줄로 세운다.
     * 잠그지 않으면 두 요청이 같은 "이전 상태"를 읽고 각자 이력을 남겨 이력 체인이 끊기고 통계가 틀어진다.
     * <p>지원 레코드를 join fetch 하지 않는 것이 핵심: PostgreSQL 은 잠금을 기다린 뒤 잠근 행만 최신 값으로 다시 읽고
     * 함께 조인한 행은 문장 시작 시점 값을 그대로 쓴다. 잠금을 얻은 뒤 별도 조회로 읽어야 앞 요청이 바꾼 상태가 보인다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from JobPosting p where p.id = :id")
    Optional<JobPosting> findWithApplicationForUpdateById(@Param("id") Long id);

    /** 마감 알림 대상: 해당 상태이면서 마감일이 주어진 날짜 중 하나인 공고(사용자 정보 포함). */
    @Query("""
            select p from JobPosting p join fetch p.application a join fetch p.user
            where a.status = :status and p.deadline in :deadlines
            """)
    List<JobPosting> findReminderCandidates(@Param("status") ApplicationStatus status,
                                            @Param("deadlines") Collection<LocalDate> deadlines);

    /**
     * 조건이 null 이면 해당 필터는 적용하지 않는다.
     * 문자열 파라미터는 cast(... as String) 으로 타입을 명시한다 — PostgreSQL 은 null 로 바인딩된 파라미터를
     * bytea 로 추론해 `varchar like bytea` 오류가 난다(H2 에서는 드러나지 않아 Testcontainers 로 잡은 문제).
     */
    @Query("""
            select p from JobPosting p join fetch p.application a
            where p.user.id = :userId
              and (:status is null or a.status = :status)
              and (:employmentType is null or p.employmentType = :employmentType)
              and (cast(:location as String) is null
                   or lower(p.location) like lower(concat('%', cast(:location as String), '%')))
              and (cast(:keyword as String) is null
                   or lower(p.company) like lower(concat('%', cast(:keyword as String), '%'))
                   or lower(p.title) like lower(concat('%', cast(:keyword as String), '%')))
            order by p.createdAt desc, p.id desc
            """)
    List<JobPosting> search(@Param("userId") Long userId,
                            @Param("status") ApplicationStatus status,
                            @Param("keyword") String keyword,
                            @Param("employmentType") EmploymentType employmentType,
                            @Param("location") String location);
}
