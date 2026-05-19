package com.sseulang.global.infra.s3;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    @Bean
    public S3Presigner s3Presigner(S3Properties props) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credentialsProvider(props));
        return builder.build();
    }

    /** 키 promotion(copy+delete) 용 동기 S3 클라이언트 — follow-up #12. */
    @Bean
    public S3Client s3Client(S3Properties props) {
        return S3Client.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credentialsProvider(props))
                .build();
    }

    private static AwsCredentialsProvider credentialsProvider(S3Properties props) {
        if (props.accessKey() != null && !props.accessKey().isBlank()
                && props.secretKey() != null && !props.secretKey().isBlank()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.accessKey(), props.secretKey())
            );
        }
        // accessKey 비어있으면 DefaultCredentialsProvider 폴백 (env / IAM role / shared profile)
        return DefaultCredentialsProvider.create();
    }
}
