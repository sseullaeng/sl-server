package com.sseulang.domain.file.application;

import com.sseulang.domain.file.domain.PresignedUrlGenerator;
import com.sseulang.domain.file.domain.PresignedUrlResult;

import java.time.Duration;

public class FakePresignedUrlGenerator implements PresignedUrlGenerator {

    @Override
    public PresignedUrlResult generate(String key, String contentType, Duration expiresIn) {
        String url = "https://fake.s3/" + key
                + "?ct=" + contentType
                + "&exp=" + expiresIn.toSeconds();
        return new PresignedUrlResult(url, key);
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
        // 단위 테스트 — no-op.
    }
}
