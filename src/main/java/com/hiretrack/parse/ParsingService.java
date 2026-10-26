package com.hiretrack.parse;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hiretrack.common.error.BusinessException;
import com.hiretrack.common.error.ErrorCode;
import com.hiretrack.parse.dto.ParseResponse;
import com.hiretrack.parse.fetch.FetchException;
import com.hiretrack.parse.fetch.PageFetcher;
import com.hiretrack.parse.fetch.UrlGuard;
import com.hiretrack.parse.parser.PostingParser;

/**
 * URL 1회 요청 → 도메인에 맞는 파서부터 순서대로 실행.
 * 앞 파서가 못 찾은 항목은 뒤 파서(JSON-LD → Open Graph)의 결과로 채운다.
 * 목록 페이지 순회 같은 자동 수집은 하지 않는다 — 사용자가 직접 넣은 URL 을 그때 한 번만 요청한다.
 */
@Service
public class ParsingService {

    private static final Logger log = LoggerFactory.getLogger(ParsingService.class);
    static final String FAILURE_MESSAGE = "자동 추출에 실패했어요. 직접 입력해 주세요.";

    private final List<PostingParser> parsers;
    private final UrlGuard urlGuard;
    private final PageFetcher fetcher;
    private final ParseRateLimiter rateLimiter;
    private final ParserStats stats;

    /** parsers 는 @Order 순으로 주입된다. */
    public ParsingService(List<PostingParser> parsers, UrlGuard urlGuard, PageFetcher fetcher,
                          ParseRateLimiter rateLimiter, ParserStats stats) {
        this.parsers = parsers;
        this.urlGuard = urlGuard;
        this.fetcher = fetcher;
        this.rateLimiter = rateLimiter;
        this.stats = stats;
    }

    public ParseResponse parse(Long userId, String url) {
        rateLimiter.acquire(userId); // DNS 조회 전에 먼저 제한해 검증 자체를 남용하지 못하게 한다
        URI uri = urlGuard.validate(url);

        Document document;
        try {
            document = fetcher.fetch(uri);
        } catch (FetchException e) {
            log.info("parse-fetch-failed host={} reason={}", uri.getHost(), e.getMessage());
            return ParseResponse.failure(FAILURE_MESSAGE + " (" + e.getMessage() + ")");
        }

        return runParsers(document, hostOf(document, uri));
    }

    /**
     * 확장 프로그램이 보낸, 사용자 브라우저에서 이미 렌더링된 HTML 을 파싱한다.
     * 자바스크립트로 그리는 페이지·로그인해야 보이는 공고도 읽을 수 있다(서버 요청 방식의 한계 보완).
     * 서버는 URL 로 요청을 보내지 않으므로 SSRF 검사 대신 형식만 확인하고, 호스트는 파서 선택에만 쓴다.
     */
    public ParseResponse parseHtml(Long userId, String url, String html) {
        rateLimiter.acquire(userId);
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new BusinessException(ErrorCode.INVALID_URL);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
            throw new BusinessException(ErrorCode.INVALID_URL);
        }
        Document document = Jsoup.parse(html, uri.toString());
        return runParsers(document, uri.getHost().toLowerCase(Locale.ROOT));
    }

    /** 2단계 서버 추출과 4단계 확장 프로그램 추출이 같은 파서 체인을 쓴다(전략 패턴 재사용) */
    private ParseResponse runParsers(Document document, String host) {
        ParsedPosting result = ParsedPosting.EMPTY;
        ParseSource primary = ParseSource.NONE;

        for (PostingParser parser : parsers) {
            if (!parser.supports(host)) continue;
            ParsedPosting parsed = runSafely(parser, document, host);
            stats.record(parser.source(), host, parsed.hasCore(), parser.siteSpecific());
            if (primary == ParseSource.NONE && parsed.hasCore()) primary = parser.source();
            result = result.fillFrom(parsed);
            if (result.hasAllFields()) break;
        }

        return result.hasCore() ? ParseResponse.success(primary, result) : ParseResponse.failure(FAILURE_MESSAGE);
    }

    /** 한 파서가 예외를 던져도 다음 파서로 넘어간다. */
    private static ParsedPosting runSafely(PostingParser parser, Document document, String host) {
        try {
            return parser.parse(document);
        } catch (RuntimeException e) {
            log.warn("parser-error source={} host={}", parser.source(), host, e);
            return ParsedPosting.EMPTY;
        }
    }

    /** 리다이렉트 후 최종 주소 기준으로 파서를 고른다. */
    private static String hostOf(Document document, URI requested) {
        try {
            String host = URI.create(document.location()).getHost();
            if (host != null) return host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            // location 이 비어 있거나 형식이 이상하면 요청 주소를 쓴다
        }
        return requested.getHost().toLowerCase(Locale.ROOT);
    }
}
