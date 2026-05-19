-- V30: 리뷰 한줄평(comment) 공개여부 토글 — 대상자(reviewee) 가 본인 페이지에서 가릴 수 있도록.
-- 별점은 항상 공개. 텍스트만 마스킹 대상.
-- 디폴트 TRUE — 기존 리뷰는 공개 상태 유지.

ALTER TABLE reviews
    ADD COLUMN content_visible TINYINT(1) NOT NULL DEFAULT 1
    COMMENT '한줄평(comment) 공개여부. 대상자가 토글. 별점/리뷰 자체는 항상 공개.';
