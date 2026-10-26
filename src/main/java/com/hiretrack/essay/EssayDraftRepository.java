package com.hiretrack.essay;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hiretrack.essay.domain.EssayDraft;

public interface EssayDraftRepository extends JpaRepository<EssayDraft, Long> {

    @Modifying
    @Query("delete from EssayDraft d where d.questionId in (select q.id from EssayQuestion q where q.posting.id = :postingId)")
    void deleteByPostingId(@Param("postingId") Long postingId);
}
