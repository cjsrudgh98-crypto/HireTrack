package com.hiretrack.essay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.hiretrack.common.error.BusinessException;
import com.hiretrack.common.error.ErrorCode;
import com.hiretrack.essay.domain.EssayAnswer;
import com.hiretrack.essay.dto.EssayDtos.AnswerRequest;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class EssayApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EssayService essayService;
    @Autowired private EssayAnswerRepository answerRepository;
    @Autowired private EssayQuestionRepository questionRepository;

    @Test
    void 버전은_덮어쓰지_않고_쌓이며_오래된_baseVersion으로_저장하면_409() throws Exception {
        Session me = signup();
        long postingId = me.createPosting();
        long qid = me.createQuestion(postingId, "지원동기를 작성해 주세요.", 500, "MOTIVATION");

        me.saveVersion(qid, "첫 번째 답변", null).andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1));
        me.saveVersion(qid, "두 번째 답변 abc", 1).andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.charCount").value(11)); // 공백 포함
        // 다른 탭이 v1 을 보고 있던 상태에서 저장 → 이미 v2 가 있으므로 충돌
        me.saveVersion(qid, "다른 탭에서 쓴 답변", 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        me.perform(get("/api/questions/{qid}/answers", qid))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[1].content").value("첫 번째 답변"));
        me.perform(get("/api/questions/{qid}/answers/{v}", qid, 1)).andExpect(jsonPath("$.content").value("첫 번째 답변"));
        me.perform(get("/api/questions/{qid}/answers/{v}", qid, 9)).andExpect(status().isNotFound());
    }

    @Test
    void 처음_지원하면_각_문항의_최신_버전이_제출본으로_고정되고_이후_수정은_거부된다() throws Exception {
        Session me = signup();
        long postingId = me.createPosting();
        long written = me.createQuestion(postingId, "지원동기", 500, "MOTIVATION");
        long empty = me.createQuestion(postingId, "협업 경험", 800, "COLLABORATION");
        me.saveVersion(written, "v1", null);
        me.saveVersion(written, "v2 최종", 1);
        me.perform(put("/api/questions/{qid}/draft", written).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"임시저장\"}")).andExpect(status().isOk());

        me.changeStatus(postingId, "APPLIED");

        me.perform(get("/api/postings/{id}/questions", postingId))
                .andExpect(jsonPath("$[0].submittedVersion").value(2))
                .andExpect(jsonPath("$[0].locked").value(true))
                .andExpect(jsonPath("$[1].locked").value(false)); // 답변이 없던 문항은 고정할 게 없음

        me.saveVersion(written, "제출 후 수정", 2).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ANSWER_LOCKED"));
        me.perform(put("/api/questions/{qid}/draft", written).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"x\"}")).andExpect(status().isConflict());
        me.perform(put("/api/questions/{qid}", written).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"바뀐 질문\",\"maxLength\":500,\"category\":\"MOTIVATION\"}")).andExpect(status().isConflict());
        me.perform(delete("/api/questions/{qid}", written)).andExpect(status().isConflict());

        // 이후 단계로 가도 제출본은 그대로
        me.changeStatus(postingId, "DOCUMENT_PASSED");
        me.perform(get("/api/questions/{qid}/answers", written))
                .andExpect(jsonPath("$[0].submitted").value(true))
                .andExpect(jsonPath("$[1].submitted").value(false));

        // 지원 후 추가로 쓴 문항은 직접 제출본 지정
        me.saveVersion(empty, "협업 답변", null);
        me.perform(post("/api/questions/{qid}/answers/{v}/submit", empty, 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.submitted").value(true));
        me.perform(post("/api/questions/{qid}/answers/{v}/submit", empty, 1)).andExpect(status().isConflict());
    }

    @Test
    void 지원_처리를_바로_되돌리면_제출본_고정도_풀린다() throws Exception {
        Session me = signup();
        long postingId = me.createPosting();
        long qid = me.createQuestion(postingId, "지원동기", null, "MOTIVATION");
        me.saveVersion(qid, "답변", null);

        me.changeStatus(postingId, "APPLIED");
        me.changeStatus(postingId, "INTERESTED"); // 10분 안의 되돌림

        me.perform(get("/api/postings/{id}/questions", postingId)).andExpect(jsonPath("$[0].locked").value(false));
        me.saveVersion(qid, "되돌린 뒤 수정", 1).andExpect(status().isCreated());
        me.perform(get("/api/postings/{id}/history", postingId))
                .andExpect(jsonPath("$[1].reverted").value(true))
                .andExpect(jsonPath("$[2].reverted").value(true));
    }

    @Test
    void 동시에_같은_문항을_저장하면_하나만_성공하고_나머지는_VERSION_CONFLICT() throws Exception {
        Session me = signup();
        long qid = me.createQuestion(me.createPosting(), "직무역량", 1000, "COMPETENCY");
        int threads = 5;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<Object>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                int n = i;
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return essayService.saveVersion(me.userId, qid, new AnswerRequest("탭 " + n, null, null));
                    } catch (BusinessException e) {
                        return e.getErrorCode();
                    }
                }));
            }
            ready.await();
            go.countDown();
        }

        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> f : results) outcomes.add(f.get());
        assertThat(outcomes).filteredOn(o -> o == ErrorCode.VERSION_CONFLICT).hasSize(threads - 1);
        assertThat(answerRepository.findByQuestionIdOrderByVersionDesc(qid)).singleElement()
                .extracting(EssayAnswer::getVersion).isEqualTo(1);
    }

    @Test
    void 유니크_제약이_같은_버전_번호를_DB_수준에서_거부한다() throws Exception {
        Session me = signup();
        long qid = me.createQuestion(me.createPosting(), "기타", null, "OTHER");
        var question = questionRepository.findById(qid).orElseThrow();
        answerRepository.saveAndFlush(new EssayAnswer(question, 1, "a", null, Instant.now()));

        assertThatThrownBy(() -> answerRepository.saveAndFlush(new EssayAnswer(question, 1, "b", null, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 템플릿에서_가져온_답변은_출처가_남고_남의_템플릿은_쓸_수_없다() throws Exception {
        Session me = signup();
        Session other = signup();
        long qid = me.createQuestion(me.createPosting(), "협업 경험", 700, "COLLABORATION");

        long templateId = me.createTemplate("COLLABORATION", "갈등 조율 경험", "팀 프로젝트에서 의견이 갈렸을 때...");
        long othersTemplate = other.createTemplate("COLLABORATION", "남의 것", "...");

        me.perform(get("/api/templates").param("category", "COLLABORATION")).andExpect(jsonPath("$.length()").value(1));
        me.perform(get("/api/templates").param("category", "MOTIVATION")).andExpect(jsonPath("$.length()").value(0));

        me.perform(post("/api/questions/{qid}/answers", qid).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"팀 프로젝트에서 의견이 갈렸을 때 수정본\",\"baseVersion\":null,\"sourceTemplateId\":" + templateId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceTemplateId").value(templateId));
        me.perform(post("/api/questions/{qid}/answers", qid).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"x\",\"baseVersion\":1,\"sourceTemplateId\":" + othersTemplate + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"));

        other.perform(put("/api/templates/{id}", templateId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"OTHER\",\"title\":\"t\",\"content\":\"c\"}")).andExpect(status().isForbidden());
        // 템플릿 출신 답변을 고쳐 쓴 버전도 출처를 이어받는다
        me.saveVersion(qid, "한 번 더 다듬은 답변", 1).andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceTemplateId").value(templateId));

        me.perform(delete("/api/templates/{id}", templateId)).andExpect(status().isNoContent());
        // 템플릿을 지워도 답변의 출처 기록은 남는다
        me.perform(get("/api/questions/{qid}/answers/1", qid)).andExpect(jsonPath("$.sourceTemplateId").value(templateId));
    }

    @Test
    void 과거_답변_검색은_문항별_최신_버전만_찾는다() throws Exception {
        Session me = signup();
        long qid = me.createQuestion(me.createPosting(), "협업 경험을 적어 주세요", 700, "COLLABORATION");
        me.saveVersion(qid, "혼자 해결했습니다", null);
        me.saveVersion(qid, "동료와 협업해서 해결했습니다", 1);
        long other = me.createQuestion(me.createPosting(), "지원동기", 700, "MOTIVATION");
        me.saveVersion(other, "회사의 비전에 공감", null);

        me.perform(get("/api/answers/search").param("keyword", "협업"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[0].company").value("○○테크"));
        me.perform(get("/api/answers/search").param("keyword", "혼자")).andExpect(jsonPath("$.length()").value(0));
        me.perform(get("/api/answers/search").param("category", "MOTIVATION").param("excludeQuestionId", String.valueOf(qid)))
                .andExpect(jsonPath("$.length()").value(1));
        signup().perform(get("/api/answers/search").param("keyword", "협업")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 남의_공고_문항에는_접근할_수_없고_공고를_지우면_자소서도_지워진다() throws Exception {
        Session me = signup();
        Session other = signup();
        long postingId = me.createPosting();
        long qid = me.createQuestion(postingId, "지원동기", 500, "MOTIVATION");
        me.saveVersion(qid, "답변", null);

        other.perform(get("/api/postings/{id}/questions", postingId)).andExpect(status().isForbidden());
        other.perform(get("/api/questions/{qid}/answers", qid)).andExpect(status().isForbidden());
        other.saveVersion(qid, "남의 답변", 1).andExpect(status().isForbidden());

        me.perform(delete("/api/postings/{id}", postingId)).andExpect(status().isNoContent());
        assertThat(questionRepository.findById(qid)).isEmpty();
        assertThat(answerRepository.findByQuestionIdOrderByVersionDesc(qid)).isEmpty();
    }

    // ---------- helpers ----------

    private Session signup() throws Exception {
        String response = mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"essay-" + UUID.randomUUID() + "@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Session("Bearer " + JsonPath.read(response, "$.accessToken"),
                ((Number) JsonPath.read(response, "$.user.id")).longValue());
    }

    private final class Session {
        final String token;
        final Long userId;

        Session(String token, Long userId) {
            this.token = token;
            this.userId = userId;
        }

        ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
            return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, token));
        }

        long createPosting() throws Exception {
            return id(perform(post("/api/postings").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"company\":\"○○테크\",\"title\":\"백엔드 개발자\"}")).andExpect(status().isCreated()));
        }

        long createQuestion(long postingId, String question, Integer maxLength, String category) throws Exception {
            return id(perform(post("/api/postings/{id}/questions", postingId).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"question\":\"" + question + "\",\"maxLength\":" + maxLength + ",\"category\":\"" + category + "\"}"))
                    .andExpect(status().isCreated()));
        }

        long createTemplate(String category, String title, String content) throws Exception {
            return id(perform(post("/api/templates").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"category\":\"" + category + "\",\"title\":\"" + title + "\",\"content\":\"" + content + "\"}"))
                    .andExpect(status().isCreated()));
        }

        ResultActions saveVersion(long qid, String content, Integer baseVersion) throws Exception {
            return perform(post("/api/questions/{qid}/answers", qid).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"content\":\"" + content + "\",\"baseVersion\":" + baseVersion + "}"));
        }

        void changeStatus(long postingId, String status) throws Exception {
            perform(patch("/api/postings/{id}/status", postingId).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"" + status + "\"}")).andExpect(status().isOk());
        }

        private long id(ResultActions actions) throws Exception {
            String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            return ((Number) JsonPath.read(body, "$.id")).longValue();
        }
    }
}
