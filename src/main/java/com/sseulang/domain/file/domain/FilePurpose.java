package com.sseulang.domain.file.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "S3 업로드 용도 — PROFILE/ITEM/SUPPORT/ESCROW 는 일반 사용자 발급 가능. NOTICE/BANNER/MESSAGE 는 도메인 전용.")
public enum FilePurpose {
    PROFILE("profiles"),
    ITEM("items"),
    MESSAGE("messages"),
    NOTICE("notices"),
    BANNER("banners"),
    SUPPORT("support"),
    ESCROW("escrow");

    private final String folder;

    FilePurpose(String folder) {
        this.folder = folder;
    }

    public String folder() {
        return folder;
    }
}
