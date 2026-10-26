package com.hiretrack.common.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 키별로 "최근 window 동안 몇 번" 을 세는 메모리 카운터 (요청 제한·로그인 실패 횟수에 사용).
 * 단일 서버 기준이며, 서버를 여러 대 띄우면 Redis 등 공유 저장소로 옮겨야 한다.
 * 기록이 모두 지난 키는 다음 접근 때 지워서 메모리가 계속 늘지 않게 한다.
 */
public class SlidingWindowCounter<K> {

    private final Duration window;
    private final Map<K, Deque<Instant>> events = new ConcurrentHashMap<>();

    public SlidingWindowCounter(Duration window) {
        this.window = window;
    }

    /** limit 미만이면 이번 요청을 기록하고 true, 이미 limit 이면 기록하지 않고 false */
    public boolean tryAcquire(K key, int limit, Instant now) {
        boolean[] acquired = {false};
        events.compute(key, (k, deque) -> {
            Deque<Instant> d = deque == null ? new ArrayDeque<>() : deque;
            evictOld(d, now);
            if (d.size() < limit) {
                d.addLast(now);
                acquired[0] = true;
            }
            return d.isEmpty() ? null : d;
        });
        return acquired[0];
    }

    public void record(K key, Instant now) {
        events.compute(key, (k, deque) -> {
            Deque<Instant> d = deque == null ? new ArrayDeque<>() : deque;
            evictOld(d, now);
            d.addLast(now);
            return d;
        });
    }

    public int count(K key, Instant now) {
        int[] count = {0};
        events.computeIfPresent(key, (k, d) -> {
            evictOld(d, now);
            count[0] = d.size();
            return d.isEmpty() ? null : d;
        });
        return count[0];
    }

    public void reset(K key) {
        events.remove(key);
    }

    int trackedKeys() {
        return events.size();
    }

    private void evictOld(Deque<Instant> d, Instant now) {
        Instant threshold = now.minus(window);
        while (!d.isEmpty() && !d.peekFirst().isAfter(threshold)) {
            d.pollFirst();
        }
    }
}
