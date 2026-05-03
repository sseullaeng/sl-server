package com.sseulang.domain.support.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 1:1 문의 카테고리. EnumType.STRING 으로 DB 저장 — 한글 이름 그대로.
 *
 * <p>Notice 와 동일 패턴 (한글 enum). FAQ/QNA 게시글({@link SupportPost}) 도 같은 enum 공유.</p>
 */
@Schema(description = "고객지원 카테고리 — 계정/거래/결제/배송/기타")
public enum InquiryCategory {
    계정,
    거래,
    결제,
    배송,
    기타
}
