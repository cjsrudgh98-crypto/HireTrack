package com.hiretrack.parse.parser;

import org.jsoup.nodes.Document;

import com.hiretrack.parse.ParseSource;
import com.hiretrack.parse.ParsedPosting;

/**
 * 공고 페이지 파서(전략 패턴). 새 사이트는 이 인터페이스를 구현한 @Component 하나만 추가하면 된다.
 * 실행 순서는 @Order 로 정한다 — 사이트 전용(10~) → JSON-LD(100) → Open Graph(200).
 */
public interface PostingParser {

    /** 이 파서가 처리할 도메인인지. 범용 파서는 항상 true. */
    boolean supports(String host);

    /** 찾지 못한 항목은 null 로 둔다. 아무것도 못 찾으면 {@link ParsedPosting#EMPTY}. */
    ParsedPosting parse(Document document);

    ParseSource source();

    /** 사이트 전용 파서인지 — 실패율 모니터링 대상. */
    default boolean siteSpecific() {
        return true;
    }
}
