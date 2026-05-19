package com.sseulang.global.exception;

import com.sseulang.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(
            BusinessException ex, HttpServletRequest request
    ) {
        ErrorCode code = ex.getErrorCode();
        log.warn("[Business] {} {} - {} ({})", request.getMethod(), request.getRequestURI(), code.name(), ex.getMessage());
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.name(), ex.getMessage(), traceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("[validation] {}", message);
        return badRequest(ErrorCode.INVALID_REQUEST, message);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("[bind] {}", message);
        return badRequest(ErrorCode.INVALID_REQUEST, message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadJson(Exception ex) {
        log.warn("[bad-json] {} — mostCause={}", ex.getMessage(), rootCauseMessage(ex));
        return badRequest(ErrorCode.INVALID_REQUEST, "요청 형식이 올바르지 않습니다.");
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception ex) {
        ErrorCode code = ErrorCode.RESOURCE_NOT_FOUND;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.name(), code.getDefaultMessage(), traceId()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        ErrorCode code = ErrorCode.METHOD_NOT_ALLOWED;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.name(), code.getDefaultMessage(), traceId()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        ErrorCode code = ErrorCode.FORBIDDEN;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.name(), code.getDefaultMessage(), traceId()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        ErrorCode code = ErrorCode.AUTH_TOKEN_INVALID;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.name(), code.getDefaultMessage(), traceId()));
    }

    /**
     * JPA optimistic lock 충돌 — 주로 OAuth takeover 같은 read-modify-write 가 동시에 실행될 때
     * (User @Version). 5xx 로 떨어지면 운영 노이즈 + 사용자 혼란이라 409 (CONFLICT) 로 변환 —
     * 사용자가 다시 시도하면 winner 의 변경이 반영된 상태라 정상 흐름 (게이트 1 round 3 보강).
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(
            OptimisticLockingFailureException ex, HttpServletRequest request
    ) {
        log.warn("[OptimisticLock] {} {} - {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        ErrorCode code = ErrorCode.AUTH_EMAIL_ALREADY_LINKED_TO_DIFFERENT_PROVIDER;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.name(), code.getDefaultMessage(), traceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex, HttpServletRequest request) {
        log.error("[Unhandled] {} {} - {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        ErrorCode code = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.name(), code.getDefaultMessage(), traceId()));
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(ErrorCode code, String message) {
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.name(), message, traceId()));
    }

    private String traceId() {
        return MDC.get("traceId");
    }
}
