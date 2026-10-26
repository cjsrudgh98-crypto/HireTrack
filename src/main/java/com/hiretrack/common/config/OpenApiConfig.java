package com.hiretrack.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** Swagger UI(/swagger-ui.html) — 컨트롤러에서 자동 생성되므로 API 문서를 따로 관리하지 않는다. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer";

    @Bean
    public OpenAPI hireTrackOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("HireTrack API")
                        .version("1.0")
                        .description("""
                                채용 지원 트래커 API. 인증: `POST /api/auth/login` 으로 받은 토큰을 [Authorize] 에 넣는다.
                                에러는 항상 `{"code": "...", "message": "..."}` 형식."""))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
