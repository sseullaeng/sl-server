package com.sseulang.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // 공통
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 메서드입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // 인증/인가
    AUTH_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTH_TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "인증 토큰이 없습니다."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    AUTH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "폐기된 토큰입니다."),
    AUTH_REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
    AUTH_OAUTH_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다."),

    // 사용자
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    USER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    USER_BLOCKED(HttpStatus.FORBIDDEN, "차단된 계정입니다."),

    // 카테고리
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),

    // 물품
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "물품을 찾을 수 없습니다."),
    ITEM_FORBIDDEN(HttpStatus.FORBIDDEN, "물품에 대한 권한이 없습니다."),
    ITEM_IMAGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "이미지는 최대 5장까지 등록할 수 있습니다."),
    ITEM_INVALID_STATE(HttpStatus.BAD_REQUEST, "현재 상태에서 수행할 수 없는 동작입니다."),

    // 거래
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "거래를 찾을 수 없습니다."),
    TRANSACTION_FORBIDDEN(HttpStatus.FORBIDDEN, "거래에 대한 권한이 없습니다."),
    TRANSACTION_INVALID_STATE(HttpStatus.BAD_REQUEST, "현재 상태에서 수행할 수 없는 동작입니다."),
    TRANSACTION_RESERVED_BY_OTHER(HttpStatus.CONFLICT, "이미 다른 사용자와 예약된 거래입니다."),
    TRANSACTION_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "본인의 물품으로는 거래를 시작할 수 없습니다."),
    TRANSACTION_COMPLETION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "거래 완료 처리는 결제·포인트 도메인 합류 후 활성화됩니다."),

    // 결제 / 포인트
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 정보를 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액이 일치하지 않습니다."),
    PAYMENT_DUPLICATED(HttpStatus.CONFLICT, "이미 처리된 결제입니다."),
    PAYMENT_VERIFY_FAILED(HttpStatus.BAD_REQUEST, "결제 검증에 실패했습니다."),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 결제입니다 (PG 측 상태)."),
    INSUFFICIENT_POINT(HttpStatus.BAD_REQUEST, "포인트가 부족합니다."),
    WITHDRAWAL_NOT_FOUND(HttpStatus.NOT_FOUND, "출금 신청을 찾을 수 없습니다."),
    WITHDRAWAL_NOT_CANCELABLE(HttpStatus.BAD_REQUEST, "취소할 수 없는 상태입니다."),
    WITHDRAWAL_INVALID_STATE(HttpStatus.BAD_REQUEST, "현재 상태에서 수행할 수 없는 동작입니다."),
    WITHDRAWAL_FORBIDDEN(HttpStatus.FORBIDDEN, "출금 신청에 대한 권한이 없습니다."),
    WITHDRAWAL_IDEMPOTENCY_MISMATCH(HttpStatus.CONFLICT, "동일 idempotencyKey 로 다른 내용의 신청이 들어왔습니다."),

    // 채팅 / 알림
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
    CHAT_FORBIDDEN(HttpStatus.FORBIDDEN, "채팅방에 접근할 권한이 없습니다."),

    // 리뷰
    REVIEW_DUPLICATED(HttpStatus.CONFLICT, "이미 작성한 리뷰입니다."),
    REVIEW_PERIOD_EXPIRED(HttpStatus.BAD_REQUEST, "리뷰 작성 기간이 지났습니다."),

    // 파일
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다."),
    FILE_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "파일 검증에 실패했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
