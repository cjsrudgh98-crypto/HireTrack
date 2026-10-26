package com.hiretrack.interview;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hiretrack.interview.domain.Interview;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

    List<Interview> findByPostingIdOrderByStartsAtAsc(Long postingId);

    @Query("select i from Interview i join fetch i.posting p join fetch p.application where i.id = :id")
    Optional<Interview> findWithPostingById(@Param("id") Long id);

    /** 사용자의 면접 중 from 이후에 시작하는 것 (공고 정보 포함) */
    @Query("""
            select i from Interview i join fetch i.posting p
            where p.user.id = :userId and i.startsAt >= :from
            order by i.startsAt asc
            """)
    List<Interview> findUpcoming(@Param("userId") Long userId, @Param("from") Instant from);

    /** 캘린더 피드용: 사용자의 모든 면접 */
    @Query("select i from Interview i join fetch i.posting p where p.user.id = :userId order by i.startsAt asc")
    List<Interview> findAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from Interview i where i.posting.id = :postingId")
    void deleteByPostingId(@Param("postingId") Long postingId);
}
