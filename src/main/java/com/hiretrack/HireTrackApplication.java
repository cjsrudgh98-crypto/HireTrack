package com.hiretrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// 인증은 JWT 필터가 담당하므로 기본 인메모리 사용자(생성 비밀번호)는 만들지 않는다.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class HireTrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(HireTrackApplication.class, args);
    }
}
