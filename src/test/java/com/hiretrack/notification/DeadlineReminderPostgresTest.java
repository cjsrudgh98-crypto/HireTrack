package com.hiretrack.notification;

import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.hiretrack.support.PostgresTestConfig;

/** 알림 대상 조회·중복 발송 방지(유니크 제약)·시간대 계산을 실제 PostgreSQL 에서 */
@Testcontainers(disabledWithoutDocker = true)
@Import(PostgresTestConfig.class)
class DeadlineReminderPostgresTest extends DeadlineReminderServiceTest {
}
