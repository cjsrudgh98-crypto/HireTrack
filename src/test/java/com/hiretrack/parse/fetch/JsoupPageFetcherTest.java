package com.hiretrack.parse.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hiretrack.common.error.BusinessException;
import com.hiretrack.common.error.ErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 로컬 HTTP 서버로 리다이렉트·타임아웃·Content-Type 처리를 검증한다.
 * 첫 요청 주소는 이미 검증됐다고 보고(fetch 는 검증된 URI 를 받음) 로컬 서버를 직접 호출한다.
 */
class JsoupPageFetcherTest {

    private HttpServer server;
    private String base;
    private final JsoupPageFetcher fetcher = new JsoupPageFetcher(new UrlGuard(HostResolver.system()), 1000, 1024);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", ex -> respond(ex, 200, "text/html; charset=utf-8",
                "<html><head><title>공고</title></head><body>" + "가".repeat(2000) + "</body></html>"));
        server.createContext("/to-metadata", ex -> redirect(ex, "http://169.254.169.254/latest/meta-data/"));
        server.createContext("/to-file", ex -> redirect(ex, "file:///etc/passwd"));
        server.createContext("/json", ex -> respond(ex, 200, "application/json", "{}"));
        server.createContext("/missing", ex -> respond(ex, 404, "text/html", "not found"));
        server.createContext("/slow", ex -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            respond(ex, 200, "text/html", "<html></html>");
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void HTML_을_읽고_본문_크기_상한을_넘는_부분은_버린다() {
        var document = fetcher.fetch(URI.create(base + "/ok"));

        assertThat(document.title()).isEqualTo("공고");
        assertThat(document.outerHtml().getBytes(StandardCharsets.UTF_8).length).isLessThan(2000);
    }

    @Test
    void 리다이렉트로_내부_주소에_가려고_하면_거부() {
        assertThatThrownBy(() -> fetcher.fetch(URI.create(base + "/to-metadata")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_URL);
        assertThatThrownBy(() -> fetcher.fetch(URI.create(base + "/to-file")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void HTML이_아니거나_오류_응답이거나_느리면_FetchException() {
        assertThatThrownBy(() -> fetcher.fetch(URI.create(base + "/json"))).isInstanceOf(FetchException.class);
        assertThatThrownBy(() -> fetcher.fetch(URI.create(base + "/missing")))
                .isInstanceOf(FetchException.class).hasMessage("HTTP 404");
        assertThatThrownBy(() -> fetcher.fetch(URI.create(base + "/slow")))
                .isInstanceOf(FetchException.class).hasMessageContaining("시간");
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        } catch (IOException ignored) {
            // 클라이언트가 상한까지만 읽고 끊으면 쓰기가 실패할 수 있다
        }
        exchange.close();
    }
}
