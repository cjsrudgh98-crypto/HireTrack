package com.hiretrack.essay;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hiretrack.essay.domain.EssayQuestion;

import jakarta.persistence.LockModeType;

public interface EssayQuestionRepository extends JpaRepository<EssayQuestion, Long> {

    List<EssayQuestion> findByPostingIdOrderByDisplayOrderAscIdAsc(Long postingId);

    @Query("select q from EssayQuestion q join fetch q.posting p join fetch p.application where q.id = :id")
    Optional<EssayQuestion> findWithPostingById(@Param("id") Long id);

    /** 문항당 한 줄인 임시저장을 처음 만들 때 동시 요청(여러 탭의 자동 저장)이 둘 다 INSERT 하지 않도록 문항 행을 잠근다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from EssayQuestion q where q.id = :id")
    Optional<EssayQuestion> findForUpdateById(@Param("id") Long id);

    @Query("select coalesce(max(q.displayOrder), 0) from EssayQuestion q where q.posting.id = :postingId")
    int maxDisplayOrder(@Param("postingId") Long postingId);

    @Modifying
    @Query("delete from EssayQuestion q where q.posting.id = :postingId")
    void deleteByPostingId(@Param("postingId") Long postingId);
}
