package com.hiretrack.stats.dto;

import java.time.LocalDate;
import java.util.List;

/** 통계 API 응답. 비율은 항상 분자·분모를 함께 내려서 "33% (1/3)"처럼 표본 크기를 보여줄 수 있게 한다. */
public final class StatsDtos {

    private StatsDtos() {
    }

    /** rate 는 분모가 0 이면 null (0% 와 "데이터 없음"을 구분) */
    public record Ratio(int numerator, int denominator, Double rate) {

        public static Ratio of(int numerator, int denominator) {
            return new Ratio(numerator, denominator, denominator == 0 ? null : (double) numerator / denominator);
        }
    }

    /**
     * 한 단계의 통과율.
     *
     * @param decided 결과가 나온 건 기준 (통과 / (통과 + 이 단계에서 불합격))
     * @param overall 전체 기준 (통과 / 이 단계에 들어온 전체) — 결과 대기 중인 건도 분모에 포함
     * @param pending 이 단계에서 결과를 기다리는 건수
     */
    public record StageRate(Ratio decided, Ratio overall, int pending) {
    }

    public record SummaryResponse(
            int totalPostings,
            int totalApplied,
            StageRate documentPass,
            StageRate interview,
            StageRate finalAccept,
            int acceptedCount
    ) {
    }

    /** @param conversion 직전 단계 대비 전환율 (첫 단계는 null) */
    public record FunnelStep(String stage, String label, int count, Ratio conversion) {
    }

    public record WeeklyPoint(LocalDate weekStart, int count) {
    }

    public record Bucket(String label, int count) {
    }

    /**
     * @param count       응답이 온 지원 건수 (평균·중앙값의 표본)
     * @param waiting     지원 기록 후 아직 응답이 없는 건수
     */
    public record ResponseTimeResponse(int count, Double averageDays, Double medianDays, int waiting, List<Bucket> buckets) {
    }

    public record BreakdownRow(String key, String label, int applied, int passed, int failed, int pending,
                               Ratio decided, Ratio overall) {
    }

    public record BreakdownResponse(String by, String metric, List<BreakdownRow> rows) {
    }
}
