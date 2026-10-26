package com.hiretrack.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 실제 PostgreSQL 컨테이너. @ServiceConnection 이 datasource 설정을 컨테이너로 바꿔 끼운다.
 * 운영과 같은 DB 에서 Flyway 마이그레이션, 엔티티 검증(ddl validate), 통계 쿼리, 유니크 제약 동시성을 확인하기 위함.
 * (H2 는 PostgreSQL 모드여도 잠금·타입·문법이 완전히 같지 않다)
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {

    /** 운영 docker-compose 와 같은 메이저 버전 */
    public static final String IMAGE = "postgres:17-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(IMAGE);
    }
}
