package com.sseulang.domain.file.domain;

/**
 * 업로드 용도. S3 폴더 구조 1단계 — {@code {purpose}/{ownerId}/{uuid}.{ext}}.
 *
 * <p>가이드 §4.5 폴더 구조와 정합. {@code messages/{roomId}/...} 처럼 owner 의미가 다른 케이스도
 * controller 에서 owner 를 적절히 결정해 전달한다.</p>
 */
public enum FilePurpose {
    PROFILE("profiles"),
    ITEM("items"),
    MESSAGE("messages"),
    NOTICE("notices"),
    BANNER("banners");

    private final String folder;

    FilePurpose(String folder) {
        this.folder = folder;
    }

    public String folder() {
        return folder;
    }
}
