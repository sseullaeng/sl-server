package com.sseulang.domain.support.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 고객지원 게시판 글 종류. FAQ(자주 묻는 질문) / QNA(공개 Q&amp;A).
 *
 * <p>구조가 동일해 단일 테이블 + 컬럼 구분. 카드 UI 만 type 별 분기.</p>
 */
@Schema(description = "FAQ / QNA")
public enum SupportPostType {
    FAQ,
    QNA
}
