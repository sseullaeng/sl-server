package com.sseulang.domain.file.infrastructure.s3;

import com.sseulang.domain.file.domain.PresignedUrlGenerator;
import com.sseulang.domain.file.domain.PresignedUrlResult;
import com.sseulang.global.infra.s3.S3Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Component
public class S3PresignedUrlGenerator implements PresignedUrlGenerator {

    private static final Logger log = LoggerFactory.getLogger(S3PresignedUrlGenerator.class);

    private final S3Presigner presigner;
    private final S3Client s3Client;
    private final String bucket;

    public S3PresignedUrlGenerator(S3Presigner presigner, S3Client s3Client, S3Properties props) {
        this.presigner = presigner;
        this.s3Client = s3Client;
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

    /**
     * sourceUrl 안에서 fromPrefix 가 toPrefix 로 치환되는 키를 찾아 S3 copy + 원본 delete.
     *
     * <p>실패는 RuntimeException 으로 전파 — 호출 트랜잭션이 롤백되어 DB 상태가 임시 url 에 머문다
     * (사용자는 재시도하면 된다). copy 만 성공하고 delete 가 실패한 경우는 src 가 garbage 로 남고
     * dst 는 정상 작동 — lifecycle 정책으로 정리.</p>
     */
    @Override
    public String promote(String sourceUrl, String fromPrefix, String toPrefix) {
        if (sourceUrl == null || fromPrefix == null || toPrefix == null) {
            return sourceUrl;
        }
        int idx = sourceUrl.indexOf(fromPrefix);
        if (idx < 0) {
            return sourceUrl;  // 이미 승격됐거나 다른 prefix — no-op
        }
        String fromKey = sourceUrl.substring(idx);
        String toKey = toPrefix + fromKey.substring(fromPrefix.length());

        try {
            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(bucket).sourceKey(fromKey)
                    .destinationBucket(bucket).destinationKey(toKey)
                    .build());
        } catch (S3Exception e) {
            throw new RuntimeException("S3 copy 실패 src=" + fromKey + " dst=" + toKey, e);
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket).key(fromKey).build());
        } catch (S3Exception e) {
            // delete 실패는 best-effort — copy 는 이미 성공했으므로 dst 사용 가능. 로깅만.
            log.warn("[s3] promote delete 실패 — src={} 잔여, lifecycle 정리 의존", fromKey, e);
        }

        return sourceUrl.substring(0, idx) + toPrefix + fromKey.substring(fromPrefix.length());
    }
}
