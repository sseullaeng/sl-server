package com.sseulang.domain.file.application;

import com.sseulang.domain.file.application.dto.PresignRequestItem;
import com.sseulang.domain.file.application.dto.PresignResult;
import com.sseulang.domain.file.domain.FilePurpose;
import com.sseulang.domain.file.domain.PresignedUrlGenerator;
import com.sseulang.domain.file.domain.PresignedUrlResult;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 이미지 업로드용 S3 presigned URL 발급. 가이드 §4.5:
 *
 * <ul>
 *   <li>Content-Type: {@code image/*}</li>
 *   <li>Content-Length: ≤ 5MB</li>
 *   <li>만료: 5분</li>
 *   <li>파일명: 백엔드 UUID 강제 (사용자 입력 무시)</li>
 *   <li>key 패턴: {@code {purpose}/{ownerId}/{uuid}.{ext}}</li>
 * </ul>
 *
 * <p><b>일반 사용자 엔드포인트의 화이트리스트</b> — Codex 게이트 2 (2026-04-29) 권한 누수 보강:
 * 일반 사용자는 자기 자원에 해당하는 {@link FilePurpose#PROFILE} / {@link FilePurpose#ITEM} 만 발급 가능.
 * {@code NOTICE} / {@code BANNER} 는 관리자 도메인이, {@code MESSAGE} 는 chat 도메인이 각자
 * 별도 엔드포인트로 직접 호출해야 하며 본 메서드는 거부({@link ErrorCode#FORBIDDEN})한다.</p>
 *
 * <p><b>폴더 구조 후속 정리(TODO)</b>: 가이드 §4.5 는 {@code items/{itemId}/...} 를 명시하지만
 * 등록 전엔 itemId 가 없어 본 PR 에선 {@code items/{userId}/...} 로 임시 보관. 등록 시 백엔드가
 * S3 copy 로 정식 폴더로 이동하는 흐름은 후속 작업.</p>
 */
@Service
public class FileApplicationService {

    private static final long MAX_CONTENT_LENGTH = 5L * 1024 * 1024;
    private static final Duration PRESIGN_EXPIRE = Duration.ofMinutes(5);
    private static final int MAX_FILES_PER_REQUEST = 10;

    /** 일반 사용자가 직접 호출 가능한 purpose. 그 외는 도메인 전용 엔드포인트로 분리. */
    private static final Set<FilePurpose> USER_ALLOWED_PURPOSES = EnumSet.of(
            FilePurpose.PROFILE,
            FilePurpose.ITEM
    );

    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/jpg",  "jpg",
            "image/png",  "png",
            "image/webp", "webp",
            "image/gif",  "gif"
    );

    private final PresignedUrlGenerator generator;

    public FileApplicationService(PresignedUrlGenerator generator) {
        this.generator = generator;
    }

    /**
     * 일반 사용자 진입점. {@link #USER_ALLOWED_PURPOSES} 외 purpose 는 거부한다.
     */
    public List<PresignResult> issueForUser(FilePurpose purpose, Long ownerId, List<PresignRequestItem> files) {
        if (purpose == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (!USER_ALLOWED_PURPOSES.contains(purpose)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return issue(purpose, ownerId, files);
    }

    /**
     * 도메인 내부(관리자·chat 등)에서 직접 호출 — purpose 화이트리스트 X. 호출자가 권한 검증 책임.
     */
    public List<PresignResult> issue(FilePurpose purpose, Long ownerId, List<PresignRequestItem> files) {
        if (purpose == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (ownerId == null || ownerId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (files.size() > MAX_FILES_PER_REQUEST) {
            throw new BusinessException(ErrorCode.FILE_VALIDATION_FAILED);
        }

        List<PresignResult> results = new ArrayList<>(files.size());
        for (PresignRequestItem f : files) {
            validate(f);
            String key = buildKey(purpose, ownerId, f.contentType());
            PresignedUrlResult issued = generator.generate(key, f.contentType(), PRESIGN_EXPIRE);
            results.add(new PresignResult(issued.presignedUrl(), issued.key()));
        }
        return results;
    }

    private static void validate(PresignRequestItem f) {
        if (f.contentType() == null || !EXTENSION_BY_CONTENT_TYPE.containsKey(f.contentType().toLowerCase())) {
            throw new BusinessException(ErrorCode.FILE_VALIDATION_FAILED);
        }
        if (f.contentLength() <= 0 || f.contentLength() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.FILE_VALIDATION_FAILED);
        }
    }

    private static String buildKey(FilePurpose purpose, Long ownerId, String contentType) {
        String ext = EXTENSION_BY_CONTENT_TYPE.get(contentType.toLowerCase());
        return purpose.folder() + "/" + ownerId + "/" + UUID.randomUUID() + "." + ext;
    }
}
