package com.sseulang.domain.file.domain;

import java.time.Duration;

/**
 * S3 등 외부 스토리지의 PUT 용 presigned URL 발급 + 키 승격(copy+delete) 인터페이스.
 * 도메인 layer 인터페이스 — 외부 SDK 의존 X. 구현은 {@code domain/file/infrastructure/s3/S3PresignedUrlGenerator}.
 */
public interface PresignedUrlGenerator {

    PresignedUrlResult generate(String key, String contentType, Duration expiresIn);

    /**
     * 임시 폴더의 객체를 정식 폴더로 복사 + 원본 삭제 (follow-up #12 옵션 A).
     *
     * <p>예: {@code items/123/uuid.jpg} → {@code items/4567/uuid.jpg}. URL 변환 책임도 본 메서드가
     * 가짐 — 호출자는 받은 새 URL 을 그대로 DB 에 저장하면 된다. dst 가 이미 존재하면 덮어쓴다 (idempotent).</p>
     *
     * @param sourceUrl  PresignedUrlResult.key 가 path 에 포함된 GET URL (또는 key 자체)
     * @param fromPrefix 원본 prefix — {@code "items/{userId}/"}
     * @param toPrefix   목적지 prefix — {@code "items/{itemId}/"}
     * @return 승격된 정식 GET URL (sourceUrl 의 prefix 만 toPrefix 로 치환). 비매칭 시 sourceUrl 반환.
     */
    String promote(String sourceUrl, String fromPrefix, String toPrefix);
}
