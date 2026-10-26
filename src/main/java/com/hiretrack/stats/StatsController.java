package com.hiretrack.stats;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hiretrack.stats.dto.StatsDtos.BreakdownResponse;
import com.hiretrack.stats.dto.StatsDtos.FunnelStep;
import com.hiretrack.stats.dto.StatsDtos.ResponseTimeResponse;
import com.hiretrack.stats.dto.StatsDtos.SummaryResponse;
import com.hiretrack.stats.dto.StatsDtos.WeeklyPoint;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/summary")
    public SummaryResponse summary(@AuthenticationPrincipal Long userId) {
        return statsService.summary(userId);
    }

    @GetMapping("/funnel")
    public List<FunnelStep> funnel(@AuthenticationPrincipal Long userId) {
        return statsService.funnel(userId);
    }

    @GetMapping("/weekly")
    public List<WeeklyPoint> weekly(@AuthenticationPrincipal Long userId,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        return statsService.weekly(userId, from);
    }

    @GetMapping("/response-time")
    public ResponseTimeResponse responseTime(@AuthenticationPrincipal Long userId) {
        return statsService.responseTime(userId);
    }

    @GetMapping("/breakdown")
    public BreakdownResponse breakdown(@AuthenticationPrincipal Long userId, @RequestParam String by) {
        return statsService.breakdown(userId, by);
    }
}
