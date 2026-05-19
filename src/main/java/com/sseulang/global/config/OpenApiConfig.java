package com.sseulang.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String COOKIE_AUTH = "cookieAuth";
    private static final String CSRF_HEADER = "csrfToken";

    @Bean
    public OpenAPI sseulangOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("쓸랭(Sseulang) API")
                        .description("""
                                중고 거래 + 대여 + 나눔 + 배달대행 통합 C2C 플랫폼.

                                **인증**: HttpOnly 쿠키 `at` (Access) / `rt` (Refresh) — 브라우저가 자동 동봉.
                                Swagger UI 에서 테스트하려면 먼저 `POST /api/v1/auth/login` 으로 로그인하세요.

                                **CSRF**: 모든 mutating(POST/PATCH/PUT/DELETE) 요청은 `X-XSRF-TOKEN` 헤더 필수.
                                값은 `XSRF-TOKEN` 쿠키에서 읽어 echo. (`/api/v1/auth/**`, `/api/v1/payments/webhook/**`,
                                `/ws-stomp*` 는 면제)

                                **응답 포맷**: 성공 `{success:true, data}`, 실패 `{success:false, error:{code,message,traceId}}`,
                                페이징 `{content, page, size, totalElements, totalPages, hasNext, hasPrevious}`.

                                **상세 ErrorCode + 연동 가이드**: `docs/FRONTEND_INTEGRATION.md`.
                                """)
                        .version("v1")
                        .contact(new Contact().name("쓸랭 백엔드").email("noreply@sseulang.test")))
                .servers(List.of(
                        new Server().url("/").description("현재 서버"),
                        new Server().url("http://localhost:8080").description("로컬")))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH).addList(CSRF_HEADER))
                .components(new Components()
                        .addSecuritySchemes(COOKIE_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("at")
                                .description("HttpOnly Access Token. 로그인 후 자동 발급. Swagger 에서는 직접 설정 X — 로그인 응답 후 자동 동봉됨."))
                        .addSecuritySchemes(CSRF_HEADER, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-XSRF-TOKEN")
                                .description("XSRF-TOKEN 쿠키 값을 echo. mutating 요청 필수.")));
    }
}
