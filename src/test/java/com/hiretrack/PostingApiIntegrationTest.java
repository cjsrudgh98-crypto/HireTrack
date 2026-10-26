package com.hiretrack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class PostingApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 공고_등록부터_상태_변경_이력까지_흐름() throws Exception {
        String token = signup();
        long id = createPosting(token);

        mockMvc.perform(patch("/api/postings/{id}/status", id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPLIED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.appliedAt").isNotEmpty());

        mockMvc.perform(get("/api/postings/{id}/history", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].fromStatus").value("INTERESTED"))
                .andExpect(jsonPath("$[1].toStatus").value("APPLIED"));

        mockMvc.perform(get("/api/postings").param("status", "APPLIED").param("keyword", "백엔드")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));
        // 조건 없이(전체), 지역만, 맞지 않는 키워드 — null 파라미터 조합이 운영 DB(PostgreSQL)에서도 동작해야 한다
        mockMvc.perform(get("/api/postings").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/postings").param("location", "서울").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/postings").param("keyword", "프론트").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(patch("/api/postings/{id}/memo", id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"memo\":\"1차 면접 준비\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memo").value("1차 면접 준비"));

        mockMvc.perform(delete("/api/postings/{id}", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/postings/{id}", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POSTING_NOT_FOUND"));
    }

    @Test
    void 다른_사용자의_공고에_접근하면_403() throws Exception {
        long id = createPosting(signup());
        String other = signup();

        mockMvc.perform(get("/api/postings/{id}", id).header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(patch("/api/postings/{id}/status", id).header(HttpHeaders.AUTHORIZATION, other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/postings").header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 토큰이_없거나_잘못되면_401() throws Exception {
        mockMvc.perform(get("/api/postings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 필수값이_없으면_400_INVALID_INPUT() throws Exception {
        String token = signup();
        mockMvc.perform(post("/api/postings").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"company\":\"\",\"title\":\"백엔드\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(patch("/api/postings/1/status").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 중복_이메일_가입은_409_잘못된_비밀번호는_401() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@test.com";
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 비밀번호를_5번_틀리면_맞는_비밀번호로도_잠시_로그인할_수_없다() throws Exception {
        String email = "lock-" + UUID.randomUUID() + "@test.com";
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized());
        }
        // 대소문자를 바꿔도 같은 계정으로 센다
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"password123\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
        mockMvc.perform(post("/api/auth/extension/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void 같은_공고에_동시에_상태를_바꿔도_이력이_이어진다() throws Exception {
        String token = signup();
        long id = createPosting(token);
        List<RequestBuilder> requests = new ArrayList<>();
        for (String s : List.of("APPLIED", "DOCUMENT_PASSED", "INTERVIEW", "REJECTED")) {
            requests.add(patch("/api/postings/{id}/status", id).header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + s + "\"}"));
        }
        assertThat(concurrently(requests)).containsOnly(200);

        String history = mockMvc.perform(get("/api/postings/{id}/history", id).header(HttpHeaders.AUTHORIZATION, token))
                .andReturn().getResponse().getContentAsString();
        List<String> from = JsonPath.read(history, "$[*].fromStatus");
        List<String> to = JsonPath.read(history, "$[*].toStatus");
        assertThat(to).hasSize(5);
        for (int i = 1; i < to.size(); i++) {
            assertThat(from.get(i)).as("이력 %d 의 이전 상태", i).isEqualTo(to.get(i - 1));
        }
        String current = JsonPath.read(mockMvc.perform(get("/api/postings/{id}", id).header(HttpHeaders.AUTHORIZATION, token))
                .andReturn().getResponse().getContentAsString(), "$.status");
        assertThat(to.getLast()).isEqualTo(current);
    }

    @Test
    void 처음_만드는_한_줄짜리_데이터는_동시_요청에도_500이_나지_않는다() throws Exception {
        String email = "race-" + UUID.randomUUID() + "@test.com";
        List<RequestBuilder> signups = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            signups.add(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"));
        }
        assertThat(concurrently(signups)).containsExactlyInAnyOrder(201, 409, 409, 409);

        String token = signup();
        List<RequestBuilder> settings = new ArrayList<>();
        List<RequestBuilder> calendars = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            settings.add(put("/api/users/me/notification-settings").header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"emailEnabled\":true,\"remindDays\":[1,3],\"sendHour\":9}"));
            calendars.add(get("/api/users/me/calendar").header(HttpHeaders.AUTHORIZATION, token));
        }
        assertThat(concurrently(settings)).containsOnly(200);
        assertThat(concurrently(calendars)).containsOnly(200);

        long postingId = createPosting(token);
        String question = mockMvc.perform(post("/api/postings/{id}/questions", postingId).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"지원 동기\",\"maxLength\":500,\"category\":\"MOTIVATION\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long questionId = ((Number) JsonPath.read(question, "$.id")).longValue();
        List<RequestBuilder> drafts = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            drafts.add(put("/api/questions/{id}/draft", questionId).header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"초안 " + i + "\"}"));
        }
        assertThat(concurrently(drafts)).containsOnly(200);
    }

    @Test
    void 잘못된_요청_방식_형식_파라미터는_500이_아니라_4xx() throws Exception {
        String token = signup();
        mockMvc.perform(put("/api/postings").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mockMvc.perform(get("/api/stats/breakdown").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    /** 요청들을 최대한 같은 순간에 보내고 상태 코드들을 돌려준다 */
    private List<Integer> concurrently(List<RequestBuilder> requests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (RequestBuilder request : requests) {
                Callable<Integer> call = () -> {
                    start.await();
                    return mockMvc.perform(request).andReturn().getResponse().getStatus();
                };
                futures.add(pool.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) statuses.add(f.get());
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private String signup() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@test.com";
        String response = mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(response, "$.accessToken");
    }

    private long createPosting(String token) throws Exception {
        String response = mockMvc.perform(post("/api/postings").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"○○테크","title":"백엔드 개발자","url":"https://example.com/jobs/1",
                                 "location":"서울 강남구","employmentType":"FULL_TIME","deadline":"2026-11-05",
                                 "requirements":"Java/Spring 경력 무관"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INTERESTED"))
                .andExpect(jsonPath("$.appliedAt").isEmpty())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }
}
