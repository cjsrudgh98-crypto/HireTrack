package com.hiretrack.parse;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.hiretrack.parse.dto.ParseHtmlRequest;
import com.hiretrack.parse.dto.ParseRequest;
import com.hiretrack.parse.dto.ParseResponse;

import jakarta.validation.Valid;

@RestController
public class ParseController {

    private final ParsingService parsingService;

    public ParseController(ParsingService parsingService) {
        this.parsingService = parsingService;
    }

    /** URL 에서 공고 정보를 추출해 돌려주기만 한다(저장하지 않음). */
    @PostMapping("/api/postings/parse")
    public ParseResponse parse(@AuthenticationPrincipal Long userId, @Valid @RequestBody ParseRequest request) {
        return parsingService.parse(userId, request.url());
    }

    /** 확장 프로그램용: 브라우저가 렌더링한 HTML 을 받아 같은 파서로 추출(서버는 외부 요청을 보내지 않음) */
    @PostMapping("/api/postings/parse-html")
    public ParseResponse parseHtml(@AuthenticationPrincipal Long userId, @Valid @RequestBody ParseHtmlRequest request) {
        return parsingService.parseHtml(userId, request.url(), request.html());
    }
}
