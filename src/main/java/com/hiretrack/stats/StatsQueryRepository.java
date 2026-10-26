package com.hiretrack.stats;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;

/**
 * 통계 집계 쿼리.
 * <p>
 * 핵심: <b>현재 상태가 아니라 status_history 의 "도달 여부"로 센다.</b>
 * 면접까지 갔다가 떨어진 공고는 현재 상태가 REJECTED 지만, 이력에 INTERVIEW 가 있으므로 서류 통과·면접 도달로 집계된다.
 * 실수로 바로 되돌린 변경(reverted)은 이력에 남아 있어도 집계에서 뺀다.
 * <p>
 * 날짜 차이 계산은 DB 마다 문법이 달라(PostgreSQL {@code ::date}, MySQL DATEDIFF 등) 시각만 뽑고
 * 서울 기준 날짜 변환은 애플리케이션에서 한다.
 */
@Repository
public class StatsQueryRepository {

    private static final String S = "com.hiretrack.posting.domain.ApplicationStatus.";

    /** 지원 건별 최고 도달 단계 + 첫 지원 시각. 인덱스 status_history(application_id, to_status) 를 탄다. */
    private static final String REACH_QUERY = """
            select new com.hiretrack.stats.ApplicationStatsRow(
                a.id, a.status, p.employmentType, p.location,
                max(case when h.toStatus = %1$sACCEPTED then 4
                         when h.toStatus = %1$sINTERVIEW then 3
                         when h.toStatus = %1$sDOCUMENT_PASSED then 2
                         when h.toStatus = %1$sAPPLIED then 1
                         else 0 end),
                min(case when h.toStatus in (%1$sAPPLIED, %1$sDOCUMENT_PASSED, %1$sINTERVIEW, %1$sACCEPTED)
                         then h.changedAt end))
            from StatusHistory h join h.application a join a.posting p
            where p.user.id = :userId and h.reverted = false
            group by a.id, a.status, p.employmentType, p.location
            """.formatted(S);

    /**
     * 지원(APPLIED 기록) 이후 첫 결과(서류 합격·이후 단계·불합격)가 나온 시각.
     * 지원 기록 없이 단계를 건너뛴 건은 "응답까지 걸린 기간"을 알 수 없으므로 빠진다.
     */
    private static final String FIRST_RESPONSE_QUERY = """
            select h.application.id,
                   (select min(h2.changedAt) from StatusHistory h2
                     where h2.application = h.application and h2.reverted = false and h2.toStatus = %1$sAPPLIED),
                   min(h.changedAt)
            from StatusHistory h
            where h.application.posting.user.id = :userId and h.reverted = false
              and h.toStatus in (%1$sDOCUMENT_PASSED, %1$sINTERVIEW, %1$sACCEPTED, %1$sREJECTED)
              and h.changedAt > (select min(h3.changedAt) from StatusHistory h3
                                  where h3.application = h.application and h3.reverted = false
                                    and h3.toStatus = %1$sAPPLIED)
            group by h.application.id
            """.formatted(S);

    /** 제출본 중 템플릿에서 파생된 것: (지원 ID, 템플릿 ID) */
    private static final String TEMPLATE_USAGE_QUERY = """
            select distinct p.application.id, a.sourceTemplateId
            from EssayAnswer a join a.question q join q.posting p
            where p.user.id = :userId and a.submitted = true and a.sourceTemplateId is not null
            """;

    private final EntityManager entityManager;

    public StatsQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<ApplicationStatsRow> reachByApplication(Long userId) {
        return entityManager.createQuery(REACH_QUERY, ApplicationStatsRow.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    public List<ResponseTime> firstResponses(Long userId) {
        return entityManager.createQuery(FIRST_RESPONSE_QUERY, Object[].class)
                .setParameter("userId", userId)
                .getResultList().stream()
                .map(r -> new ResponseTime((Long) r[0], (Instant) r[1], (Instant) r[2]))
                .toList();
    }

    /** 지원 ID → 그 지원의 제출본에 쓰인 템플릿 ID 목록 */
    public Map<Long, List<Long>> templatesByApplication(Long userId) {
        return entityManager.createQuery(TEMPLATE_USAGE_QUERY, Object[].class)
                .setParameter("userId", userId)
                .getResultList().stream()
                .collect(Collectors.groupingBy(r -> (Long) r[0],
                        Collectors.mapping(r -> (Long) r[1], Collectors.toList())));
    }

    public record ResponseTime(Long applicationId, Instant appliedAt, Instant respondedAt) {
    }
}
