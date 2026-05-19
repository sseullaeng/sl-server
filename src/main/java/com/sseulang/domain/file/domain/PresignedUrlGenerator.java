package com.sseulang.domain.file.domain;

import java.time.Duration;

public interface PresignedUrlGenerator {

    PresignedUrlResult generate(String key, String contentType, Duration expiresIn);

    

    String promote(String sourceUrl, String fromPrefix, String toPrefix);

    

    void delete(String sourceUrl);
}
