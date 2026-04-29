package com.sseulang.global.infra.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AWS S3 자격증명 + 버킷 설정. 로컬 개발 환경에서는 accessKey/secretKey 가 빈 값일 수 있음 —
 * 그 경우 {@link S3Config} 가 DefaultCredentialsProvider 로 폴백.
 */
@ConfigurationProperties(prefix = "app.aws")
public record S3Properties(
        String accessKey,
        String secretKey,
        String region,
        Bucket s3
) {
    public record Bucket(String bucket, long presignedUrlExpireSeconds) { }
}
