package com.hiretrack.common.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.hiretrack.common.error.ErrorCode;

@Configuration
public class SecurityConfig {

    /** 확장 프로그램이 보내는 HTML 본문 상한 (HTML 2MB + JSON 이스케이프 여유) */
    private static final long PARSE_HTML_MAX_BYTES = 3L * 1024 * 1024;

    private static final String APP = TokenScope.APP.authority();
    private static final String EXTENSION = TokenScope.EXTENSION.authority();

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider,
                                                   JsonErrorWriter errorWriter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> { })
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login",
                                "/api/auth/extension/login").permitAll()
                        // 캘린더 앱은 인증 헤더를 못 보낸다 — URL 의 비밀 토큰으로 인증
                        .requestMatchers(HttpMethod.GET, "/api/calendar/*").permitAll()
                        // 확장 프로그램 토큰으로 할 수 있는 일은 여기까지
                        .requestMatchers(HttpMethod.POST, "/api/postings/parse-html", "/api/postings").hasAnyAuthority(APP, EXTENSION)
                        .requestMatchers(HttpMethod.GET, "/api/users/me").hasAnyAuthority(APP, EXTENSION)
                        .requestMatchers("/api/**").hasAuthority(APP)
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> errorWriter.write(res, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, ex) -> errorWriter.write(res, ErrorCode.FORBIDDEN)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new RequestSizeLimitFilter("/api/postings/parse-html", PARSE_HTML_MAX_BYTES, errorWriter),
                        JwtAuthenticationFilter.class)
                .build();
    }

    /** 확장 프로그램(chrome-extension://{ID})에서 API 를 부를 수 있게 허용. /api 에만 적용 */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(properties.allowedOriginPatterns() == null ? List.of() : properties.allowedOriginPatterns());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
