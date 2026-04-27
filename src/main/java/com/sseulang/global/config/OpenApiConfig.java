package com.sseulang.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String COOKIE_AUTH = "cookieAuth";

    @Bean
    public OpenAPI sseulangOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("쓸랭(Sseulang) API")
                        .description("중고 거래 + 대여 + 나눔 + 배달대행 통합 C2C 플랫폼 API")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH))
                .components(new Components()
                        .addSecuritySchemes(COOKIE_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("ACCESS_TOKEN")
                                .description("HttpOnly 쿠키로 자동 전송됩니다. 브라우저에서 테스트하려면 로그인 후 사용하세요.")));
    }
}
