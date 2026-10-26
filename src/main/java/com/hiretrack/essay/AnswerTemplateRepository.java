package com.hiretrack.essay;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hiretrack.essay.domain.AnswerTemplate;
import com.hiretrack.essay.domain.EssayCategory;

public interface AnswerTemplateRepository extends JpaRepository<AnswerTemplate, Long> {

    List<AnswerTemplate> findByUserIdOrderByUpdatedAtDesc(Long userId);

    List<AnswerTemplate> findByUserIdAndCategoryOrderByUpdatedAtDesc(Long userId, EssayCategory category);
}
