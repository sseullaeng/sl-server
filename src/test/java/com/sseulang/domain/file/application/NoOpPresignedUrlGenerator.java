package com.sseulang.domain.file.application;

import com.sseulang.domain.file.domain.PresignedUrlGenerator;
import com.sseulang.domain.file.domain.PresignedUrlResult;

import java.time.Duration;

/**
 * 단위 테스트용 — generate 는 dummy URL, promote 는 단순 string replace (no S3 call).
 */
public class NoOpPresignedUrlGenerator implements PresignedUrlGenerator {

    @Override
    public PresignedUrlResult generate(String key, String contentType, Duration expiresIn) {
        return new PresignedUrlResult("https://test/" + key + "?signed", key);
    }

    @Override
    public String promote(String sourceUrl, String fromPrefix, String toPrefix) {
        if (sourceUrl == null || fromPrefix == null || toPrefix == null) return sourceUrl;
        int idx = sourceUrl.indexOf(fromPrefix);
        if (idx < 0) return sourceUrl;
        return sourceUrl.substring(0, idx) + toPrefix + sourceUrl.substring(idx + fromPrefix.length());
    }

    @Override
    public void delete(String sourceUrl) {
        // 단위 테스트 — no-op (S3 호출 없음).
    }
}
