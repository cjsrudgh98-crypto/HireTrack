package com.hiretrack.stats;

import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.hiretrack.support.PostgresTestConfig;

/** 같은 시나리오(StatsServiceTest)를 실제 PostgreSQL 에서 — 집계 쿼리가 운영 DB 에서도 같은 숫자를 내는지 */
@Testcontainers(disabledWithoutDocker = true)
@Import(PostgresTestConfig.class)
class StatsServicePostgresTest extends StatsServiceTest {
}
