package com.hiretrack;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class Phase2ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 추출_API는_로그인이_필요하고_내부_주소는_400_INVALID_URL() throws Exception {
        mockMvc.perform(post("/api/postings/parse").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://www.saramin.co.kr/\"}"))
                .andExpect(status().isUnauthorized());

        String token = signup();
        mockMvc.perform(post("/api/postings/parse").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"http://127.0.0.1:8080/api/users/me\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_URL"));
    }

    @Test
    void 추출_요청은_사용자당_분당_한도를_넘으면_429() throws Exception {
        String token = signup(); // 테스트 설정: 분당 3회
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/postings/parse").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"file:///etc/passwd\"}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/postings/parse").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"file:///etc/passwd\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void 알림_설정은_기본값으로_조회되고_변경할_수_있다() throws Exception {
        String token = signup();

        mockMvc.perform(get("/api/users/me/notification-settings").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.remindDays.length()").value(2))
                .andExpect(jsonPath("$.remindDays[0]").value(3))
                .andExpect(jsonPath("$.remindDays[1]").value(1))
                .andExpect(jsonPath("$.sendHour").value(9))
                .andExpect(jsonPath("$.email").isNotEmpty());

        mockMvc.perform(put("/api/users/me/notification-settings").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\":true,\"remindDays\":[1,7,3],\"sendHour\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remindDays[0]").value(7))
                .andExpect(jsonPath("$.remindDays[2]").value(1))
                .andExpect(jsonPath("$.sendHour").value(20));

        mockMvc.perform(get("/api/users/me/notification-settings").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.sendHour").value(20));
    }

    @Test
    void 알림_설정_검증_허용되지_않은_일수_시각_범위() throws Exception {
        String token = signup();
        for (String body : new String[]{
                "{\"emailEnabled\":true,\"remindDays\":[2],\"sendHour\":9}",
                "{\"emailEnabled\":true,\"remindDays\":[],\"sendHour\":9}",
                "{\"emailEnabled\":true,\"remindDays\":[1],\"sendHour\":24}",
                "{\"remindDays\":[1],\"sendHour\":9}"}) {
            mockMvc.perform(put("/api/users/me/notification-settings").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        // 알림을 끌 때는 시점이 비어 있어도 된다
        mockMvc.perform(put("/api/users/me/notification-settings").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"emailEnabled\":false,\"remindDays\":[],\"sendHour\":9}"))
                .andExpect(status().isOk());
    }

    private String signup() throws Exception {
        String response = mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"p2-" + UUID.randomUUID() + "@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(response, "$.accessToken");
    }
}
