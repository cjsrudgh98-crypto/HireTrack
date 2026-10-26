package com.hiretrack.parse.fetch;

import java.net.URI;

import org.jsoup.nodes.Document;

public interface PageFetcher {

    /**
     * 검증된 URL 의 HTML 을 1회 요청해 파싱한다. 리다이렉트도 hop 마다 다시 검증한다.
     *
     * @throws FetchException 연결 실패·타임아웃·HTML 아님·HTTP 오류 등
     */
    Document fetch(URI uri);
}
