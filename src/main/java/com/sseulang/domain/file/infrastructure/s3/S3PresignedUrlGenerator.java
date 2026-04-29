package com.sseulang.domain.file.infrastructure.s3;

import com.sseulang.domain.file.domain.PresignedUrlGenerator;
import com.sseulang.domain.file.domain.PresignedUrlResult;
import com.sseulang.global.infra.s3.S3Properties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Component
public class S3PresignedUrlGenerator implements PresignedUrlGenerator {

    private final S3Presigner presigner;
    private final String bucket;

    public S3PresignedUrlGenerator(S3Presigner presigner, S3Properties props) {
        this.presigner = presigner;
        this.bucket = props.s3().bucket();
    }

    @Override
    public PresignedUrlResult generate(String key, String contentType, Duration expiresIn) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest req = PutObjectPresignRequest.builder()
                .signatureDuration(expiresIn)
                .putObjectRequest(put)
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(req);
        return new PresignedUrlResult(presigned.url().toString(), key);
    }
}
