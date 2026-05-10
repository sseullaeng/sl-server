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
    AUTH_PASSWORD_INVALID(HttpStatus.BAD_REQUEST, "비밀번호 형식이 올바르지 않습니다."),
    AUTH_EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증이 필요합니다."),
    AUTH_VERIFICATION_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "유효하지 않은 인증 토큰입니다."),
    AUTH_VERIFICATION_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "만료된 인증 토큰입니다."),
    AUTH_EMAIL_ALREADY_LINKED_TO_DIFFERENT_PROVIDER(HttpStatus.CONFLICT, "이미 다른 SNS 로 가입된 이메일입니다."),
    AUTH_VERIFICATION_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "인증 메일 재발송은 잠시 후 다시 시도해 주세요."),

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
    ITEM_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "지정한 이미지를 찾을 수 없습니다."),
    ITEM_IMAGE_ORDER_MISMATCH(HttpStatus.BAD_REQUEST, "재배치 입력이 기존 이미지 set 과 일치하지 않습니다."),
    ITEM_INVALID_STATE(HttpStatus.BAD_REQUEST, "현재 상태에서 수행할 수 없는 동작입니다."),

    // 거래
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "거래를 찾을 수 없습니다."),
    TRANSACTION_FORBIDDEN(HttpStatus.FORBIDDEN, "거래에 대한 권한이 없습니다."),
    TRANSACTION_INVALID_STATE(HttpStatus.BAD_REQUEST, "현재 상태에서 수행할 수 없는 동작입니다."),
    TRANSACTION_RESERVED_BY_OTHER(HttpStatus.CONFLICT, "이미 다른 사용자와 예약된 거래입니다."),
    TRANSACTION_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "본인의 물품으로는 거래를 시작할 수 없습니다."),
    TRANSACTION_HANDOVER_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "인계 확인은 판매자만 수행할 수 있습니다."),
    TRANSACTION_RECEIVE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "인수 확인은 구매자만 수행할 수 있습니다."),
    TRANSACTION_HOLD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "거래 보관 잔액 처리에 실패했습니다."),
    TX_CHATROOM_REQUIRED(HttpStatus.BAD_REQUEST, "거래 시작은 채팅방 안에서만 가능합니다 (chatRoomId 필수)."),
    TX_CHATROOM_ITEM_MISMATCH(HttpStatus.BAD_REQUEST, "채팅방의 물품과 거래 요청 물품이 다릅니다."),
    TX_ALREADY_ACTIVE_IN_ROOM(HttpStatus.BAD_REQUEST, "해당 채팅방에 진행 중인 거래가 이미 있습니다."),
    TX_SELLER_ONLY(HttpStatus.FORBIDDEN, "거래 시작은 판매자만 가능합니다."),

    // 결제 / 포인트
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 정보를 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액이 일치하지 않습니다."),
    PAYMENT_DUPLICATED(HttpStatus.CONFLICT, "이미 처리된 결제입니다."),
    PAYMENT_VERIFY_FAILED(HttpStatus.BAD_REQUEST, "결제 검증에 실패했습니다."),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 결제입니다 (PG 측 상태)."),
    INSUFFICIENT_POINT(HttpStatus.BAD_REQUEST, "포인트가 부족합니다."),
    PAYMENT_WEBHOOK_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "Webhook 시그니처가 유효하지 않습니다."),
    PAYMENT_WEBHOOK_TIMESTAMP_INVALID(HttpStatus.BAD_REQUEST, "Webhook timestamp 가 유효하지 않습니다."),
    PAYMENT_WEBHOOK_REPLAY_REJECTED(HttpStatus.UNAUTHORIZED, "Webhook 이 허용 시간 범위를 벗어났습니다."),
    PAYMENT_WEBHOOK_PAYLOAD_INVALID(HttpStatus.BAD_REQUEST, "Webhook payload 가 유효하지 않습니다."),
    WITHDRAWAL_NOT_FOUND(HttpStatus.NOT_FOUND, "출금 신청을 찾을 수 없습니다."),
    WITHDRAWAL_NOT_CANCELABLE(HttpStatus.BAD_REQUEST, "취소할 수 없는 상태입니다."),
    WITHDRAWAL_INVALID_STATE(HttpStatus.BAD_REQUEST, "현재 상태에서 수행할 수 없는 동작입니다."),
    WITHDRAWAL_FORBIDDEN(HttpStatus.FORBIDDEN, "출금 신청에 대한 권한이 없습니다."),
    WITHDRAWAL_IDEMPOTENCY_MISMATCH(HttpStatus.CONFLICT, "동일 idempotencyKey 로 다른 내용의 신청이 들어왔습니다."),

    // 채팅 / 알림
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
    CHAT_FORBIDDEN(HttpStatus.FORBIDDEN, "채팅방에 접근할 권한이 없습니다."),
    CHAT_ROOM_OPPONENT_LEFT(HttpStatus.BAD_REQUEST, "상대방이 채팅방을 나가서 더 이상 메시지를 보낼 수 없습니다."),
    CHAT_ROOM_ALREADY_LEFT(HttpStatus.BAD_REQUEST, "이미 나간 채팅방입니다."),

    // 리뷰
    REVIEW_DUPLICATED(HttpStatus.CONFLICT, "이미 작성한 리뷰입니다."),
    REVIEW_PERIOD_EXPIRED(HttpStatus.BAD_REQUEST, "리뷰 작성 기간이 지났습니다."),

    // 공지 / 배너
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "공지를 찾을 수 없습니다."),
    BANNER_NOT_FOUND(HttpStatus.NOT_FOUND, "배너를 찾을 수 없습니다."),

    // 신고
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "신고를 찾을 수 없습니다."),
    REPORT_INVALID_STATE(HttpStatus.BAD_REQUEST, "현재 상태에서 수행할 수 없는 동작입니다."),

    // 파일
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다."),
    FILE_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "파일 검증에 실패했습니다."),

    // 고객지원 (1:1 문의 / FAQ·QNA)
    INQUIRY_NOT_FOUND(HttpStatus.NOT_FOUND, "문의를 찾을 수 없습니다."),
    INQUIRY_FORBIDDEN(HttpStatus.FORBIDDEN, "본인 문의에서만 가능합니다."),
    INQUIRY_INVALID_STATE(HttpStatus.BAD_REQUEST, "현재 상태에서 수행할 수 없는 동작입니다."),
    SUPPORT_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),

    // 배달대행
    DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "배달 요청을 찾을 수 없습니다."),
    DELIVERY_FORBIDDEN(HttpStatus.FORBIDDEN, "배달 요청에 대한 권한이 없습니다."),
    DELIVERY_INVALID_STATE(HttpStatus.BAD_REQUEST, "현재 상태에서 수행할 수 없는 동작입니다."),
    DELIVERY_ALREADY_ACCEPTED(HttpStatus.CONFLICT, "이미 다른 라이더가 수락한 요청입니다."),
    DELIVERY_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "본인이 등록한 요청은 수락할 수 없습니다."),
    DELIVERY_LOCATION_INVALID(HttpStatus.BAD_REQUEST, "유효하지 않은 위치 좌표입니다."),
    DELIVERY_LOCATION_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "위치 업데이트 빈도가 너무 높습니다."),
    DELIVERY_NOT_RIDER(HttpStatus.FORBIDDEN, "라이더 권한이 없습니다."),

    // ===== Escrow (거래대행) =====
    ESCROW_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "거래대행 링크를 찾을 수 없습니다."),
    ESCROW_LINK_EXPIRED(HttpStatus.GONE, "거래대행 링크가 만료되었습니다."),
    ESCROW_LINK_ALREADY_TAKEN(HttpStatus.CONFLICT, "이미 다른 사용자가 참여한 링크입니다."),
    ESCROW_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "본인이 생성한 링크에는 참여할 수 없습니다."),
    ESCROW_NOT_FOUND(HttpStatus.NOT_FOUND, "거래대행 신청을 찾을 수 없습니다."),
    ESCROW_FORBIDDEN(HttpStatus.FORBIDDEN, "거래대행 신청에 대한 권한이 없습니다."),
    ESCROW_INVALID_STATE(HttpStatus.BAD_REQUEST, "현재 상태에서 수행할 수 없는 동작입니다."),
    ESCROW_FORM_INVALID(HttpStatus.BAD_REQUEST, "거래대행 신청 입력값이 올바르지 않습니다."),
    ESCROW_FEE_MISMATCH(HttpStatus.BAD_REQUEST, "수수료 정책이 변경되었습니다. 새 금액 확인 후 다시 시도해주세요."),
    ESCROW_CHATROOM_REQUIRED(HttpStatus.BAD_REQUEST, "거래대행 신청은 채팅방 안에서만 가능합니다 (chatRoomId 필수)."),
    ESCROW_TX_CHATROOM_MISMATCH(HttpStatus.BAD_REQUEST, "쓸랭 거래의 채팅방과 거래대행 신청 채팅방이 다릅니다."),
    ESCROW_SELLER_ONLY(HttpStatus.FORBIDDEN, "거래대행 신청은 판매자만 가능합니다."),

    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "요청 본문 크기가 한도를 초과합니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
