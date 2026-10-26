package com.hiretrack;

import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.hiretrack.support.PostgresTestConfig;

/** Phase4ApiIntegrationTest 를 실제 PostgreSQL 에서 — H2 에서는 통과하지만 운영 DB 에서 깨지는 쿼리를 잡는다 */
@Testcontainers(disabledWithoutDocker = true)
@Import(PostgresTestConfig.class)
class Phase4ApiPostgresTest extends Phase4ApiIntegrationTest {
}
