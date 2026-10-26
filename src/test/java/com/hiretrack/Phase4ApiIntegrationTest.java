package com.hiretrack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class Phase4ApiIntegrationTest {

    private static final String EXTENSION_ORIGIN = "chrome-extension://abcdefghijklmnopabcdefghijklmnop";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // ---------- 확장 프로그램: 범위 제한 토큰 ----------

    @Test
    void 확장_프로그램_토큰은_공고_추출과_등록만_할_수_있다() throws Exception {
        String email = signup();
        String ext = "Bearer " + JsonPath.read(perform(post("/api/auth/extension/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");

        perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, ext)).andExpect(status().isOk());
        perform(post("/api/postings/parse-html").header(HttpHeaders.AUTHORIZATION, ext)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("url", "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=1",
                        "html", fixture("saramin.html")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.source").value("SARAMIN"))
                .andExpect(jsonPath("$.data.company").value("○○테크"));
        perform(post("/api/postings").header(HttpHeaders.AUTHORIZATION, ext).contentType(MediaType.APPLICATION_JSON)
                .content("{\"company\":\"○○테크\",\"title\":\"백엔드\"}")).andExpect(status().isCreated());

        // 그 밖의 API 는 403 — 토큰이 새어도 자소서·통계·목록은 못 본다
        perform(get("/api/postings").header(HttpHeaders.AUTHORIZATION, ext))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        perform(get("/api/stats/summary").header(HttpHeaders.AUTHORIZATION, ext)).andExpect(status().isForbidden());
        perform(get("/api/templates").header(HttpHeaders.AUTHORIZATION, ext)).andExpect(status().isForbidden());
    }

    @Test
    void 확장_프로그램_출처의_CORS_preflight만_허용한다() throws Exception {
        perform(options("/api/postings/parse-html").header(HttpHeaders.ORIGIN, EXTENSION_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, EXTENSION_ORIGIN));

        perform(options("/api/postings/parse-html").header(HttpHeaders.ORIGIN, "https://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void parse_html_은_크기_상한과_URL_형식을_검사한다() throws Exception {
        String token = appToken(signup());
        perform(post("/api/postings/parse-html").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("url", "https://example.com/", "html", "가".repeat(3 * 1024 * 1024 / 3 + 10)))))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
        perform(post("/api/postings/parse-html").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("url", "file:///etc/passwd", "html", "<html></html>"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_URL"));
    }

    // ---------- 면접 일정 ----------

    @Test
    void 면접_일정_등록_수정_다가오는_목록_권한() throws Exception {
        String me = appToken(signup());
        String other = appToken(signup());
        long postingId = createPosting(me, "2099-12-31");

        String created = perform(post("/api/postings/{id}/interviews", postingId).header(HttpHeaders.AUTHORIZATION, me)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"FIRST\",\"startsAt\":\"2099-11-03T14:00:00+09:00\",\"location\":\"본사 3층\",\"memo\":\"포트폴리오 지참\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andExpect(jsonPath("$.endsAt").value("2099-11-03T15:00:00+09:00"))
                .andExpect(jsonPath("$.postingStatus").value("INTERESTED"))
                .andReturn().getResponse().getContentAsString();
        long interviewId = ((Number) JsonPath.read(created, "$.id")).longValue();

        perform(get("/api/interviews/upcoming").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].company").value("○○테크"));
        perform(put("/api/interviews/{id}", interviewId).header(HttpHeaders.AUTHORIZATION, me).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"SECOND\",\"startsAt\":\"2099-11-04T10:00:00+09:00\",\"durationMinutes\":90}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("SECOND")).andExpect(jsonPath("$.location").isEmpty());

        perform(get("/api/postings/{id}/interviews", postingId).header(HttpHeaders.AUTHORIZATION, other)).andExpect(status().isForbidden());
        perform(delete("/api/interviews/{id}", interviewId).header(HttpHeaders.AUTHORIZATION, other)).andExpect(status().isForbidden());
        perform(get("/api/interviews/upcoming").header(HttpHeaders.AUTHORIZATION, other)).andExpect(jsonPath("$.length()").value(0));

        // 공고를 지우면 면접 일정도 함께 지워진다
        perform(delete("/api/postings/{id}", postingId).header(HttpHeaders.AUTHORIZATION, me)).andExpect(status().isNoContent());
        perform(get("/api/interviews/upcoming").header(HttpHeaders.AUTHORIZATION, me)).andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- 캘린더 구독 피드 ----------

    @Test
    void 캘린더_피드에는_지원_전_공고의_마감일과_면접이_고정_UID로_들어간다() throws Exception {
        String me = appToken(signup());
        long interested = createPosting(me, "2099-11-05");
        long applied = createPosting(me, "2099-11-06");
        perform(patch("/api/postings/{id}/status", applied).header(HttpHeaders.AUTHORIZATION, me)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPLIED\"}")).andExpect(status().isOk());
        String interview = perform(post("/api/postings/{id}/interviews", applied).header(HttpHeaders.AUTHORIZATION, me)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CODING_TEST\",\"startsAt\":\"2099-11-10T14:00:00+09:00\",\"location\":\"온라인, Zoom\"}"))
                .andReturn().getResponse().getContentAsString();
        long interviewId = ((Number) JsonPath.read(interview, "$.id")).longValue();

        String url = JsonPath.read(perform(get("/api/users/me/calendar").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.url");
        String path = url.substring(url.indexOf("/api/calendar/"));

        String ics = perform(get(path)) // 인증 헤더 없이
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String unfolded = ics.replace("\r\n ", "");
        assertThat(unfolded)
                .contains("UID:posting-" + interested + "-deadline@hiretrack", "DTSTART;VALUE=DATE:20991105")
                .doesNotContain("UID:posting-" + applied + "-deadline@hiretrack") // 이미 지원한 공고의 마감일은 제외
                .contains("UID:interview-" + interviewId + "@hiretrack", "DTSTART:20991110T050000Z", "DTEND:20991110T060000Z")
                .contains("SUMMARY:[코딩테스트] ○○테크 · 백엔드 개발자", "LOCATION:온라인\\, Zoom", "TRIGGER:-PT60M")
                .contains("?tab=essay");

        // 재발급하면 이전 URL 은 바로 막힌다
        String newUrl = JsonPath.read(perform(post("/api/users/me/calendar/regenerate").header(HttpHeaders.AUTHORIZATION, me))
                .andReturn().getResponse().getContentAsString(), "$.url");
        assertThat(newUrl).isNotEqualTo(url);
        perform(get(path)).andExpect(status().isNotFound());
        perform(get(newUrl.substring(newUrl.indexOf("/api/calendar/")))).andExpect(status().isOk());
        perform(get("/api/calendar/not-a-real-token.ics")).andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request);
    }

    private String signup() throws Exception {
        String email = "p4-" + UUID.randomUUID() + "@test.com";
        perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}")).andExpect(status().isCreated());
        return email;
    }

    private String appToken(String email) throws Exception {
        return "Bearer " + JsonPath.read(perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private long createPosting(String token, String deadline) throws Exception {
        String body = perform(post("/api/postings").header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"company\":\"○○테크\",\"title\":\"백엔드 개발자\",\"deadline\":\"" + deadline + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = Phase4ApiIntegrationTest.class.getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
