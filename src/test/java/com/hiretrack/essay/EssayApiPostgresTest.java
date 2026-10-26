package com.hiretrack.essay;

import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.hiretrack.support.PostgresTestConfig;

/** 자소서 시나리오 — 특히 동시 버전 저장(유니크 제약·잠금 동작)을 실제 PostgreSQL 에서 */
@Testcontainers(disabledWithoutDocker = true)
@Import(PostgresTestConfig.class)
class EssayApiPostgresTest extends EssayApiIntegrationTest {
}
