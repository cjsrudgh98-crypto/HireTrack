package com.hiretrack.essay;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hiretrack.essay.domain.EssayCategory;
import com.hiretrack.essay.dto.EssayDtos.TemplateRequest;
import com.hiretrack.essay.dto.EssayDtos.TemplateResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public List<TemplateResponse> list(@AuthenticationPrincipal Long userId,
                                       @RequestParam(required = false) EssayCategory category) {
        return templateService.list(userId, category);
    }

    @GetMapping("/{id}")
    public TemplateResponse get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return templateService.get(userId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse create(@AuthenticationPrincipal Long userId, @Valid @RequestBody TemplateRequest request) {
        return templateService.create(userId, request);
    }

    @PutMapping("/{id}")
    public TemplateResponse update(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                   @Valid @RequestBody TemplateRequest request) {
        return templateService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        templateService.delete(userId, id);
    }
}
