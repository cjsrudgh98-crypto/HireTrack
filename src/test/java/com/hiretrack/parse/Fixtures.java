package com.hiretrack.parse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.hiretrack.common.config.TimeConfig;
import com.hiretrack.parse.parser.JobKoreaParser;
import com.hiretrack.parse.parser.JsonLdParser;
import com.hiretrack.parse.parser.OpenGraphParser;
import com.hiretrack.parse.parser.PostingParser;
import com.hiretrack.parse.parser.SaraminParser;

import tools.jackson.databind.json.JsonMapper;

/** 저장해 둔 HTML 픽스처를 읽어 네트워크 없이 파서를 검증하기 위한 도우미. */
public final class Fixtures {

    /** 2026-10-26 09:00 (서울) */
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), TimeConfig.ZONE);

    private Fixtures() {
    }

    public static Document load(String name, String location) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) throw new IllegalArgumentException("fixture not found: " + name);
            return Jsoup.parse(in, StandardCharsets.UTF_8.name(), location);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 운영과 같은 @Order 순서의 파서 목록 */
    public static List<PostingParser> parsers() {
        return List.of(
                new SaraminParser(CLOCK),
                new JobKoreaParser(CLOCK),
                new JsonLdParser(JsonMapper.builder().build(), CLOCK),
                new OpenGraphParser());
    }
}
