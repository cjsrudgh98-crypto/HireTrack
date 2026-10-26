package com.hiretrack.essay;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hiretrack.essay.domain.EssayAnswer;
import com.hiretrack.essay.domain.EssayCategory;

public interface EssayAnswerRepository extends JpaRepository<EssayAnswer, Long> {

    List<EssayAnswer> findByQuestionIdOrderByVersionDesc(Long questionId);

    Optional<EssayAnswer> findByQuestionIdAndVersion(Long questionId, int version);

    /** 각 문항의 최신 버전 */
    @Query("""
            select a from EssayAnswer a
            where a.question.id in :questionIds
              and a.version = (select max(a2.version) from EssayAnswer a2 where a2.question = a.question)
            """)
    List<EssayAnswer> findLatestByQuestionIds(@Param("questionIds") Collection<Long> questionIds);

    List<EssayAnswer> findByQuestionIdInAndSubmittedTrue(Collection<Long> questionIds);

    @Query("select a from EssayAnswer a where a.question.posting.id = :postingId and a.submitted = true")
    List<EssayAnswer> findSubmittedByPostingId(@Param("postingId") Long postingId);

    /**
     * 내 과거 답변 검색 — 문항마다 최신 버전 하나만, 내용이나 질문에 키워드가 있는 것.
     * excludeQuestionId 는 "지금 쓰는 문항"을 추천 목록에서 빼기 위해 쓴다.
     */
    @Query("""
            select a from EssayAnswer a join fetch a.question q join fetch q.posting p
            where p.user.id = :userId
              and a.version = (select max(a2.version) from EssayAnswer a2 where a2.question = q)
              and (:category is null or q.category = :category)
              and (:excludeQuestionId is null or q.id <> :excludeQuestionId)
              and (cast(:keyword as String) is null
                   or lower(a.content) like lower(concat('%', cast(:keyword as String), '%'))
                   or lower(q.question) like lower(concat('%', cast(:keyword as String), '%')))
            order by a.createdAt desc
            """)
    List<EssayAnswer> search(@Param("userId") Long userId,
                             @Param("keyword") String keyword,
                             @Param("category") EssayCategory category,
                             @Param("excludeQuestionId") Long excludeQuestionId,
                             Pageable pageable);

    @Modifying
    @Query("delete from EssayAnswer a where a.question.id = :questionId")
    void deleteByQuestionId(@Param("questionId") Long questionId);

    @Modifying
    @Query("delete from EssayAnswer a where a.question.id in (select q.id from EssayQuestion q where q.posting.id = :postingId)")
    void deleteByPostingId(@Param("postingId") Long postingId);
}
