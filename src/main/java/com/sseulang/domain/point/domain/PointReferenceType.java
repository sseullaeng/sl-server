package com.sseulang.domain.point.domain;

/**
 * point_histories.reference_type 분류. 잔액 변동의 원인 도메인 식별.
 * VARCHAR(30) 매핑이라 enum 이름 그대로 저장.
 */
public enum PointReferenceType {
    PAYMENT,
    TRANSACTION,
    WITHDRAWAL
}
