package com.hiretrack.stats;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hiretrack.common.config.TimeConfig;
import com.hiretrack.common.error.BusinessException;
import com.hiretrack.common.error.ErrorCode;
import com.hiretrack.essay.AnswerTemplateRepository;
import com.hiretrack.essay.domain.AnswerTemplate;
import com.hiretrack.posting.domain.EmploymentType;
import com.hiretrack.stats.StatsQueryRepository.ResponseTime;
import com.hiretrack.stats.dto.StatsDtos.BreakdownResponse;
import com.hiretrack.stats.dto.StatsDtos.BreakdownRow;
import com.hiretrack.stats.dto.StatsDtos.Bucket;
import com.hiretrack.stats.dto.StatsDtos.FunnelStep;
import com.hiretrack.stats.dto.StatsDtos.Ratio;
import com.hiretrack.stats.dto.StatsDtos.ResponseTimeResponse;
import com.hiretrack.stats.dto.StatsDtos.StageRate;
import com.hiretrack.stats.dto.StatsDtos.SummaryResponse;
import com.hiretrack.stats.dto.StatsDtos.WeeklyPoint;

/**
 * 지원 통계. 개인 서비스 규모라 요청마다 집계한다(쿼리 1~2개).
 * <p>
 * 단계별 판정 (지원한 건만 대상):
 * <ul>
 *   <li>통과: 이력상 다음 단계 이상에 도달</li>
 *   <li>이 단계에서 불합격: 다음 단계에 도달하지 못했고 현재 상태가 REJECTED</li>
 *   <li>결과 대기: 그 외 — "결과가 나온 건 기준" 통과율의 분모에서 빠진다</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class StatsService {

    static final int APPLIED = 1;
    static final int DOCUMENT_PASSED = 2;
    static final int INTERVIEW = 3;
    static final int ACCEPTED = 4;
    private static final int MAX_WEEKS = 104;

    private static final Map<EmploymentType, String> EMPLOYMENT_LABEL = Map.of(
            EmploymentType.FULL_TIME, "정규직", EmploymentType.FREELANCE, "프리랜서", EmploymentType.OTHER, "기타");

    private final StatsQueryRepository statsRepository;
    private final AnswerTemplateRepository templateRepository;
    private final Clock clock;

    public StatsService(StatsQueryRepository statsRepository, AnswerTemplateRepository templateRepository, Clock clock) {
        this.statsRepository = statsRepository;
        this.templateRepository = templateRepository;
        this.clock = clock;
    }

    public SummaryResponse summary(Long userId) {
        List<ApplicationStatsRow> rows = statsRepository.reachByApplication(userId);
        List<ApplicationStatsRow> applied = rows.stream().filter(ApplicationStatsRow::applied).toList();
        return new SummaryResponse(
                rows.size(),
                applied.size(),
                stageRate(applied, APPLIED),
                stageRate(applied, DOCUMENT_PASSED),
                stageRate(applied, INTERVIEW),
                (int) applied.stream().filter(r -> r.reached(ACCEPTED)).count());
    }

    public List<FunnelStep> funnel(Long userId) {
        List<ApplicationStatsRow> rows = statsRepository.reachByApplication(userId);
        String[][] stages = {{"APPLIED", "지원"}, {"DOCUMENT_PASSED", "서류 합격"}, {"INTERVIEW", "면접"}, {"ACCEPTED", "최종 합격"}};
        List<FunnelStep> steps = new ArrayList<>();
        Integer previous = null;
        for (int i = 0; i < stages.length; i++) {
            int stage = i + 1;
            int count = (int) rows.stream().filter(r -> r.reached(stage)).count();
            steps.add(new FunnelStep(stages[i][0], stages[i][1], count, previous == null ? null : Ratio.of(count, previous)));
            previous = count;
        }
        return steps;
    }

    /** from 이 속한 주(월요일 시작)부터 이번 주까지, 지원이 없는 주도 0 으로 채운다. */
    public List<WeeklyPoint> weekly(Long userId, LocalDate from) {
        LocalDate today = LocalDate.now(clock.withZone(TimeConfig.ZONE));
        LocalDate thisWeek = weekStart(today);
        LocalDate firstWeek = weekStart(from == null ? today.minusWeeks(11) : from);
        if (firstWeek.isAfter(thisWeek) || ChronoUnit.WEEKS.between(firstWeek, thisWeek) >= MAX_WEEKS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Map<LocalDate, Long> counts = statsRepository.reachByApplication(userId).stream()
                .filter(ApplicationStatsRow::applied)
                .map(r -> weekStart(toSeoulDate(r.appliedOn())))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<WeeklyPoint> points = new ArrayList<>();
        for (LocalDate week = firstWeek; !week.isAfter(thisWeek); week = week.plusWeeks(1)) {
            points.add(new WeeklyPoint(week, counts.getOrDefault(week, 0L).intValue()));
        }
        return points;
    }

    /** 지원 후 첫 결과까지 걸린 일수(서울 날짜 기준) */
    public ResponseTimeResponse responseTime(Long userId) {
        List<ResponseTime> responses = statsRepository.firstResponses(userId);
        List<Long> days = responses.stream()
                .map(r -> ChronoUnit.DAYS.between(toSeoulDate(r.appliedAt()), toSeoulDate(r.respondedAt())))
                .sorted()
                .toList();

        // 응답 대기: 지원 단계에 머물러 있고 아직 결과가 없는 건
        int waiting = (int) statsRepository.reachByApplication(userId).stream()
                .filter(r -> r.applied() && !r.reached(DOCUMENT_PASSED) && !r.rejected())
                .count();

        Double average = days.isEmpty() ? null : days.stream().mapToLong(Long::longValue).average().orElseThrow();
        return new ResponseTimeResponse(days.size(), average, median(days), waiting, List.of(
                new Bucket("3일 이내", count(days, d -> d <= 3)),
                new Bucket("4~7일", count(days, d -> d >= 4 && d <= 7)),
                new Bucket("8~14일", count(days, d -> d >= 8 && d <= 14)),
                new Bucket("15~30일", count(days, d -> d >= 15 && d <= 30)),
                new Bucket("31일 이상", count(days, d -> d >= 31))));
    }

    /** 조건별 서류 통과율. by = employmentType / location / template */
    public BreakdownResponse breakdown(Long userId, String by) {
        List<ApplicationStatsRow> applied = statsRepository.reachByApplication(userId).stream()
                .filter(ApplicationStatsRow::applied).toList();

        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, List<ApplicationStatsRow>> groups = new LinkedHashMap<>();
        switch (by == null ? "" : by) {
            case "employmentType" -> applied.forEach(r -> {
                String key = r.employmentType() == null ? "NONE" : r.employmentType().name();
                labels.putIfAbsent(key, r.employmentType() == null ? "미지정" : EMPLOYMENT_LABEL.get(r.employmentType()));
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
            });
            case "location" -> applied.forEach(r -> {
                String region = region(r.location());
                labels.putIfAbsent(region, region);
                groups.computeIfAbsent(region, k -> new ArrayList<>()).add(r);
            });
            case "template" -> groupByTemplate(userId, applied, labels, groups);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        List<BreakdownRow> rows = groups.entrySet().stream()
                .map(e -> breakdownRow(e.getKey(), labels.get(e.getKey()), e.getValue()))
                .sorted(Comparator.comparingInt(BreakdownRow::applied).reversed().thenComparing(BreakdownRow::label))
                .toList();
        return new BreakdownResponse(by, "DOCUMENT_PASS", rows);
    }

    /**
     * 템플릿별: 제출본에 그 템플릿을 쓴 지원 건으로 묶는다. 한 지원서의 여러 문항에서 서로 다른 템플릿을 썼다면
     * 각 템플릿 그룹에 모두 들어간다. 템플릿을 하나도 안 쓴 지원은 "템플릿 미사용".
     */
    private void groupByTemplate(Long userId, List<ApplicationStatsRow> applied,
                                 Map<String, String> labels, Map<String, List<ApplicationStatsRow>> groups) {
        Map<Long, List<Long>> usage = statsRepository.templatesByApplication(userId);
        Map<Long, String> titles = templateRepository.findAllById(
                        usage.values().stream().flatMap(List::stream).distinct().toList())
                .stream().collect(Collectors.toMap(AnswerTemplate::getId, AnswerTemplate::getTitle));

        for (ApplicationStatsRow row : applied) {
            List<Long> templateIds = usage.getOrDefault(row.applicationId(), List.of());
            if (templateIds.isEmpty()) {
                labels.putIfAbsent("NONE", "템플릿 미사용");
                groups.computeIfAbsent("NONE", k -> new ArrayList<>()).add(row);
            }
            for (Long templateId : templateIds) {
                String key = String.valueOf(templateId);
                labels.putIfAbsent(key, titles.getOrDefault(templateId, "삭제된 템플릿"));
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            }
        }
    }

    private static BreakdownRow breakdownRow(String key, String label, List<ApplicationStatsRow> rows) {
        int passed = (int) rows.stream().filter(r -> r.reached(DOCUMENT_PASSED)).count();
        int failed = (int) rows.stream().filter(r -> !r.reached(DOCUMENT_PASSED) && r.rejected()).count();
        int pending = rows.size() - passed - failed;
        return new BreakdownRow(key, label, rows.size(), passed, failed, pending,
                Ratio.of(passed, passed + failed), Ratio.of(passed, rows.size()));
    }

    /**
     * stage 단계에 들어온 건 중 다음 단계로 넘어간 비율.
     * 예) stage=APPLIED → 서류 통과율, stage=DOCUMENT_PASSED → 면접 전환율
     */
    static StageRate stageRate(List<ApplicationStatsRow> applied, int stage) {
        List<ApplicationStatsRow> entered = applied.stream().filter(r -> r.reached(stage)).toList();
        int passed = (int) entered.stream().filter(r -> r.reached(stage + 1)).count();
        int failed = (int) entered.stream().filter(r -> !r.reached(stage + 1) && r.rejected()).count();
        int pending = entered.size() - passed - failed;
        return new StageRate(Ratio.of(passed, passed + failed), Ratio.of(passed, entered.size()), pending);
    }

    /** "서울 강남구" → "서울", 비어 있으면 "미지정" */
    static String region(String location) {
        if (location == null || location.isBlank()) return "미지정";
        return location.trim().split("\\s+")[0];
    }

    private static Double median(List<Long> sorted) {
        if (sorted.isEmpty()) return null;
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid) : (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }

    private static int count(List<Long> days, Predicate<Long> condition) {
        return (int) days.stream().filter(condition).count();
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static LocalDate toSeoulDate(Instant instant) {
        return instant.atZone(TimeConfig.ZONE).toLocalDate();
    }
}
