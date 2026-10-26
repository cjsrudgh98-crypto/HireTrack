package com.hiretrack.essay;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hiretrack.common.error.BusinessException;
import com.hiretrack.common.error.ErrorCode;
import com.hiretrack.essay.domain.AnswerTemplate;
import com.hiretrack.essay.domain.EssayCategory;
import com.hiretrack.essay.dto.EssayDtos.TemplateRequest;
import com.hiretrack.essay.dto.EssayDtos.TemplateResponse;
import com.hiretrack.user.UserRepository;

@Service
@Transactional(readOnly = true)
public class TemplateService {

    private final AnswerTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public TemplateService(AnswerTemplateRepository templateRepository, UserRepository userRepository, Clock clock) {
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    public List<TemplateResponse> list(Long userId, EssayCategory category) {
        List<AnswerTemplate> templates = category == null
                ? templateRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                : templateRepository.findByUserIdAndCategoryOrderByUpdatedAtDesc(userId, category);
        return templates.stream().map(TemplateResponse::from).toList();
    }

    public TemplateResponse get(Long userId, Long templateId) {
        return TemplateResponse.from(getOwned(userId, templateId));
    }

    @Transactional
    public TemplateResponse create(Long userId, TemplateRequest request) {
        AnswerTemplate template = new AnswerTemplate(userRepository.getReferenceById(userId), request.category(),
                request.title().trim(), request.content(), clock.instant());
        return TemplateResponse.from(templateRepository.save(template));
    }

    @Transactional
    public TemplateResponse update(Long userId, Long templateId, TemplateRequest request) {
        AnswerTemplate template = getOwned(userId, templateId);
        template.update(request.category(), request.title().trim(), request.content(), clock.instant());
        return TemplateResponse.from(template);
    }

    /** 템플릿을 지워도 그 템플릿에서 파생된 답변의 source_template_id 는 남는다(통계에서는 "삭제된 템플릿") */
    @Transactional
    public void delete(Long userId, Long templateId) {
        templateRepository.delete(getOwned(userId, templateId));
    }

    private AnswerTemplate getOwned(Long userId, Long templateId) {
        AnswerTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND));
        if (!template.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return template;
    }
}
