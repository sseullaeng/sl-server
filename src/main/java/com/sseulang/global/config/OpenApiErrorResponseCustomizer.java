package com.sseulang.global.config;

import com.sseulang.global.common.ApiResponse;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 모든 endpoint 에 표준 에러 응답을 일괄 등록 (401/403/404/500 + 메서드별 400).
 *
 * <p>{@link com.sseulang.global.exception.GlobalExceptionHandler} 가 실제로 던지는 응답 shape 와 일치하는
 * {@link ApiResponse} schema 를 reference 로 사용. controller 마다 {@code @ApiResponses} 를
 * 반복 작성하지 않아도 Swagger UI 에서 같은 에러 응답이 노출된다.</p>
 *
 * <p>경로/HTTP 메서드 별 분기:
 * <ul>
 *   <li>모든 operation: 401(AUTH_TOKEN_*) / 500(INTERNAL_SERVER_ERROR)</li>
 *   <li>POST/PATCH/PUT/DELETE: 400(INVALID_REQUEST) — body validation 실패</li>
 *   <li>경로에 PathVariable 포함: 404(RESOURCE_NOT_FOUND)</li>
 *   <li>{@code /api/v1/admin/**}: 403(FORBIDDEN — ROLE_ADMIN 필요)</li>
 *   <li>일반 mutating endpoint: 403(FORBIDDEN / AUTH_EMAIL_NOT_VERIFIED)</li>
 * </ul>
 *
 * <p>특정 endpoint 가 다른 응답 코드를 가지면 controller 메서드의 {@code @io.swagger.v3.oas.annotations.responses.ApiResponses}
 * 가 본 customizer 가 추가한 항목을 덮어쓴다 (springdoc merge 정책).</p>
 */
@Configuration
public class OpenApiErrorResponseCustomizer {

    private static final String JSON = "application/json";
    private static final String SCHEMA_REF = "#/components/schemas/ApiResponse";

    @Bean
    public OperationCustomizer commonErrorResponses() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            String path = resolvePath(handlerMethod);
            String method = resolveHttpMethod(handlerMethod);

            // 401 — 모든 endpoint 공통
            putIfAbsent(responses, "401", "인증 실패 — 토큰 누락/만료/위변조/폐기 또는 OAuth 검증 실패",
                    Map.of(
                            "토큰 없음", example("AUTH_TOKEN_MISSING", "인증 토큰이 없습니다."),
                            "토큰 만료", example("AUTH_TOKEN_EXPIRED", "만료된 토큰입니다."),
                            "토큰 위변조", example("AUTH_TOKEN_INVALID", "유효하지 않은 토큰입니다."),
                            "토큰 폐기", example("AUTH_TOKEN_REVOKED", "폐기된 토큰입니다.")
                    ));

            // 500 — 모든 endpoint 공통
            putIfAbsent(responses, "500", "서버 내부 오류 — traceId 로 백엔드에 문의",
                    Map.of("default", example("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.")));

            // 400 — mutating 요청
            if (isMutating(method)) {
                putIfAbsent(responses, "400", "요청 형식/값 오류 — body validation 실패",
                        Map.of("default", example("INVALID_REQUEST", "요청 형식이 올바르지 않습니다.")));
            }

            // 404 — PathVariable 가진 endpoint
            if (path != null && path.contains("{")) {
                putIfAbsent(responses, "404", "자원을 찾을 수 없음",
                        Map.of("default", example("RESOURCE_NOT_FOUND", "리소스를 찾을 수 없습니다.")));
            }

            // 403 — 관리자 영역 vs 일반 mutating
            if (path != null && path.startsWith("/api/v1/admin")) {
                putIfAbsent(responses, "403", "관리자 권한 필요 — ROLE_ADMIN 미보유",
                        Map.of("default", example("FORBIDDEN", "접근 권한이 없습니다.")));
            } else if (path != null && path.startsWith("/api/v1/")
                    && !path.startsWith("/api/v1/auth/oauth2")
                    && !path.startsWith("/api/v1/auth/signup")
                    && !path.startsWith("/api/v1/auth/login")
                    && !path.startsWith("/api/v1/auth/refresh")
                    && !path.startsWith("/api/v1/auth/logout")
                    && !path.startsWith("/api/v1/auth/verify-email")
                    && !path.startsWith("/api/v1/payments/webhook")
                    && !"GET".equals(method)) {
                putIfAbsent(responses, "403", "권한 거부 — ROLE_USER 미보유 또는 이메일 미인증",
                        Map.of(
                                "역할 부족", example("FORBIDDEN", "접근 권한이 없습니다."),
                                "이메일 미인증", example("AUTH_EMAIL_NOT_VERIFIED", "이메일 인증이 필요합니다.")
                        ));
            }

            return operation;
        };
    }

    private static io.swagger.v3.oas.models.responses.ApiResponse buildResponse(
            String description, Map<String, Example> examples
    ) {
        Schema<?> schema = new Schema<>().$ref(SCHEMA_REF);
        MediaType mt = new MediaType().schema(schema);
        Map<String, Example> orderedExamples = new LinkedHashMap<>(examples);
        mt.setExamples(orderedExamples);
        Content content = new Content().addMediaType(JSON, mt);
        return new io.swagger.v3.oas.models.responses.ApiResponse()
                .description(description)
                .content(content);
    }

    private static Example example(String code, String message) {
        Example ex = new Example();
        ex.setSummary(code);
        ex.setValue(Map.of(
                "success", false,
                "error", Map.of("code", code, "message", message, "traceId", "9f2c8c5b")
        ));
        return ex;
    }

    private static void putIfAbsent(
            ApiResponses responses, String code, String description, Map<String, Example> examples
    ) {
        if (responses.containsKey(code)) return;
        responses.addApiResponse(code, buildResponse(description, examples));
    }

    private static boolean isMutating(String method) {
        return method != null && (method.equals("POST") || method.equals("PUT")
                || method.equals("PATCH") || method.equals("DELETE"));
    }

    private static String resolvePath(HandlerMethod handlerMethod) {
        RequestMapping classMapping = handlerMethod.getBeanType().getAnnotation(RequestMapping.class);
        String classPath = (classMapping != null && classMapping.value().length > 0) ? classMapping.value()[0] : "";
        RequestMapping methodMapping = handlerMethod.getMethodAnnotation(RequestMapping.class);
        String methodPath = (methodMapping != null && methodMapping.value().length > 0) ? methodMapping.value()[0] : "";
        // method 레벨 매핑은 GetMapping 등에 path 가 있을 수 있음 — value() 추출
        if (methodPath.isEmpty()) {
            methodPath = extractMethodLevelPath(handlerMethod);
        }
        return classPath + methodPath;
    }

    private static String extractMethodLevelPath(HandlerMethod handlerMethod) {
        var get = handlerMethod.getMethodAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
        if (get != null && get.value().length > 0) return get.value()[0];
        var post = handlerMethod.getMethodAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
        if (post != null && post.value().length > 0) return post.value()[0];
        var patch = handlerMethod.getMethodAnnotation(org.springframework.web.bind.annotation.PatchMapping.class);
        if (patch != null && patch.value().length > 0) return patch.value()[0];
        var put = handlerMethod.getMethodAnnotation(org.springframework.web.bind.annotation.PutMapping.class);
        if (put != null && put.value().length > 0) return put.value()[0];
        var del = handlerMethod.getMethodAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class);
        if (del != null && del.value().length > 0) return del.value()[0];
        return "";
    }

    private static String resolveHttpMethod(HandlerMethod handlerMethod) {
        if (handlerMethod.hasMethodAnnotation(org.springframework.web.bind.annotation.GetMapping.class)) return "GET";
        if (handlerMethod.hasMethodAnnotation(org.springframework.web.bind.annotation.PostMapping.class)) return "POST";
        if (handlerMethod.hasMethodAnnotation(org.springframework.web.bind.annotation.PatchMapping.class)) return "PATCH";
        if (handlerMethod.hasMethodAnnotation(org.springframework.web.bind.annotation.PutMapping.class)) return "PUT";
        if (handlerMethod.hasMethodAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class)) return "DELETE";
        RequestMapping mapping = handlerMethod.getMethodAnnotation(RequestMapping.class);
        if (mapping != null && mapping.method().length > 0) return mapping.method()[0].name();
        return null;
    }
}
