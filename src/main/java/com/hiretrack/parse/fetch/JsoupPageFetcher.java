package com.hiretrack.parse.fetch;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;

public class JsoupPageFetcher implements PageFetcher {

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; HireTrack/1.0; personal job-application tracker)";
    private static final int MAX_REDIRECTS = 3;

    private final UrlGuard urlGuard;
    private final int timeoutMillis;
    private final int maxBodyBytes;

    public JsoupPageFetcher(UrlGuard urlGuard, int timeoutMillis, int maxBodyBytes) {
        this.urlGuard = urlGuard;
        this.timeoutMillis = timeoutMillis;
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    public Document fetch(URI uri) {
        URI current = uri;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            Connection.Response response = execute(current);
            int status = response.statusCode();
            if (status >= 300 && status < 400 && response.hasHeader("Location")) {
                // 리다이렉트를 자동으로 따라가면 공인 주소 → 내부 주소로 튀는 우회가 가능하므로 hop 마다 다시 검사
                current = urlGuard.validate(current.resolve(response.header("Location")).toString());
                continue;
            }
            if (status != 200) throw new FetchException("HTTP " + status);
            try {
                return response.parse();
            } catch (IOException e) {
                throw new FetchException("본문을 읽지 못했습니다.", e);
            }
        }
        throw new FetchException("리다이렉트가 너무 많습니다.");
    }

    private Connection.Response execute(URI uri) {
        try {
            return Jsoup.connect(uri.toString())
                    .userAgent(USER_AGENT)
                    .timeout(timeoutMillis)          // 연결 + 읽기 합산 제한
                    .maxBodySize(maxBodyBytes)       // 초과분은 잘라서 읽지 않음
                    .followRedirects(false)
                    .ignoreHttpErrors(true)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "ko-KR,ko;q=0.9")
                    .execute();
        } catch (UnsupportedMimeTypeException e) {
            throw new FetchException("HTML 페이지가 아닙니다.", e);
        } catch (SocketTimeoutException | HttpTimeoutException e) {
            // Jsoup 은 JDK HttpClient 로 요청할 때 HttpTimeoutException, HttpURLConnection 일 때 SocketTimeoutException 을 던진다
            throw new FetchException("응답 시간이 초과되었습니다.", e);
        } catch (IOException e) {
            throw new FetchException("페이지에 연결하지 못했습니다.", e);
        }
    }
}
