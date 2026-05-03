package com.sseulang.domain.file.domain;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 업로드 용도. S3 폴더 구조 1단계 — {@code {purpose}/{ownerId}/{uuid}.{ext}}.
 *
 * <p>가이드 §4.5 폴더 구조와 정합. {@code messages/{roomId}/...} 처럼 owner 의미가 다른 케이스도
 * controller 에서 owner 를 적절히 결정해 전달한다.</p>
 */
@Schema(description = "S3 업로드 용도 — PROFILE/ITEM/SUPPORT 는 일반 사용자 발급 가능. NOTICE/BANNER/MESSAGE 는 도메인 전용.")
public enum FilePurpose {
    PROFILE("profiles"),
    ITEM("items"),
    MESSAGE("messages"),
    NOTICE("notices"),
    BANNER("banners"),
    SUPPORT("support");

    private final String folder;

    FilePurpose(String folder) {
        this.folder = folder;
    }

    public String folder() {
        return folder;
    }
}
