package com.hiretrack.parse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 파서별 성공/실패 집계. 사이트 전용 파서의 실패는 누적 실패율과 함께 WARN 으로 남겨
 * 사이트 구조 변경(파서 깨짐)을 로그에서 바로 알아챌 수 있게 한다.
 * 예) grep "parser-failure source=SARAMIN"
 * <p>
 * 같은 값을 Micrometer 카운터 {@code hiretrack.parser.results{source, result}} 로도 남겨
 * /actuator/metrics 에서 사이트별 실패율을 볼 수 있다.
 */
@Component
public class ParserStats {

    private static final Logger log = LoggerFactory.getLogger(ParserStats.class);

    private final Map<ParseSource, Totals> counters = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    @Autowired
    public ParserStats(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** 테스트용 */
    public ParserStats() {
        this(new SimpleMeterRegistry());
    }

    public void record(ParseSource source, String host, boolean success, boolean siteSpecific) {
        Counter.builder("hiretrack.parser.results")
                .description("파서별 추출 성공/실패 수")
                .tag("source", source.name())
                .tag("result", success ? "success" : "failure")
                .register(meterRegistry)
                .increment();
        Totals counter = counters.computeIfAbsent(source, s -> new Totals());
        long total = counter.total.incrementAndGet();
        long failures = success ? counter.failures.get() : counter.failures.incrementAndGet();

        if (!success && siteSpecific) {
            log.warn("parser-failure source={} host={} failureRate={}/{} ({}%)",
                    source, host, failures, total, failures * 100 / total);
        } else {
            log.info("parser-result source={} host={} success={} failureRate={}/{}",
                    source, host, success, failures, total);
        }
    }

    public long failures(ParseSource source) {
        Totals counter = counters.get(source);
        return counter == null ? 0 : counter.failures.get();
    }

    public long total(ParseSource source) {
        Totals counter = counters.get(source);
        return counter == null ? 0 : counter.total.get();
    }

    private static final class Totals {
        private final AtomicLong total = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();
    }
}
