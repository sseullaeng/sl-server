package com.sseulang.domain.file.domain;

import java.time.Duration;

/**
 * S3 등 외부 스토리지의 PUT 용 presigned URL 발급 인터페이스. 도메인 layer 인터페이스 — 외부 SDK 의존 X.
 * 구현은 {@code domain/file/infrastructure/s3/S3PresignedUrlGenerator}.
 */
public interface PresignedUrlGenerator {

    PresignedUrlResult generate(String key, String contentType, Duration expiresIn);
}
