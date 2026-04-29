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
}
