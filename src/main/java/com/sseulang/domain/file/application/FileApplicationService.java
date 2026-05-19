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

@Service
public class FileApplicationService {

    private static final long MAX_CONTENT_LENGTH = 5L * 1024 * 1024;
    private static final Duration PRESIGN_EXPIRE = Duration.ofMinutes(5);
    private static final int MAX_FILES_PER_REQUEST = 10;

    
    private static final Set<FilePurpose> USER_ALLOWED_PURPOSES = EnumSet.of(
            FilePurpose.PROFILE,
            FilePurpose.ITEM,
            FilePurpose.SUPPORT,
            FilePurpose.ESCROW,
            FilePurpose.MESSAGE   // 채팅 메시지 이미지 첨부 — 채팅방 참여자 인증은 메시지 send 단계에서 수행
    );

    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/jpg",  "jpg",
            "image/png",  "png",
            "image/webp", "webp",
            "image/gif",  "gif"
    );

    private final PresignedUrlGenerator generator;
    private final com.sseulang.domain.user.application.UserApplicationService userService;

    public FileApplicationService(
            PresignedUrlGenerator generator,
            com.sseulang.domain.user.application.UserApplicationService userService
    ) {
        this.generator = generator;
        this.userService = userService;
    }

    

    public List<PresignResult> issueForUser(FilePurpose purpose, Long ownerId, List<PresignRequestItem> files) {
        if (purpose == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (!USER_ALLOWED_PURPOSES.contains(purpose)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        
        
        if (purpose != FilePurpose.PROFILE) {
            userService.requireVerified(ownerId);
        }
        return issue(purpose, ownerId, files);
    }

    

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
