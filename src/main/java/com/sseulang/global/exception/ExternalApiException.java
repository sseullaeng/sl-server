package com.sseulang.global.exception;

import lombok.Getter;

/**
 * 외부 API 호출 실패. 가이드 §3.10 — anti-corruption boundary 에서 raw 예외(SocketTimeout,
 * RestClientException 등)를 이 타입으로 wrap 하여 도메인 레이어가 외부 인프라 예외에
 * 직접 의존하지 않도록 한다.
 *
 * <p>호출자(ApplicationService 등)는 본 예외를 잡아 상황에 맞는 BusinessException 으로
 * 변환한다 (예: OAuth 흐름 → {@code AUTH_OAUTH_FAILED}).</p>
 */
@Getter
public class ExternalApiException extends RuntimeException {

    private final String externalServiceName;

    public ExternalApiException(String externalServiceName, Throwable cause) {
        super("외부 API 호출 실패: " + externalServiceName, cause);
        this.externalServiceName = externalServiceName;
    }
}
