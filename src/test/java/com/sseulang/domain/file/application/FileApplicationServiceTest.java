package com.sseulang.domain.file.application;

import com.sseulang.domain.file.application.dto.PresignRequestItem;
import com.sseulang.domain.file.application.dto.PresignResult;
import com.sseulang.domain.file.domain.FilePurpose;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileApplicationServiceTest {

    private static final Long OWNER = 100L;

    private FileApplicationService service;

    @BeforeEach
    void setUp() {
        service = new FileApplicationService(new FakePresignedUrlGenerator(), org.mockito.Mockito.mock(com.sseulang.domain.user.application.UserApplicationService.class));
    }

    @Test
    @DisplayName("issue 정상_key 패턴 = {folder}/{ownerId}/{uuid}.{ext}")
    void issue_정상() {
        List<PresignResult> results = service.issue(FilePurpose.ITEM, OWNER, List.of(
                new PresignRequestItem("image/jpeg", 1024L),
                new PresignRequestItem("image/png", 2048L)
        ));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).key()).matches("items/100/[0-9a-f-]+\\.jpg");
        assertThat(results.get(1).key()).matches("items/100/[0-9a-f-]+\\.png");
        assertThat(results.get(0).presignedUrl()).contains("ct=image/jpeg");
        assertThat(results.get(0).presignedUrl()).contains("exp=300");
    }

    @Test
    @DisplayName("issue purpose 별 폴더 매핑")
    void issue_폴더() {
        assertThat(service.issue(FilePurpose.PROFILE, OWNER, List.of(jpeg())).get(0).key())
                .startsWith("profiles/100/");
        assertThat(service.issue(FilePurpose.MESSAGE, OWNER, List.of(jpeg())).get(0).key())
                .startsWith("messages/100/");
        assertThat(service.issue(FilePurpose.NOTICE, OWNER, List.of(jpeg())).get(0).key())
                .startsWith("notices/100/");
        assertThat(service.issue(FilePurpose.BANNER, OWNER, List.of(jpeg())).get(0).key())
                .startsWith("banners/100/");
    }

    @Test
    @DisplayName("issue contentType image/* 외_거부")
    void issue_비이미지_거부() {
        assertThatThrownBy(() -> service.issue(FilePurpose.ITEM, OWNER, List.of(
                new PresignRequestItem("application/pdf", 1024L)
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_VALIDATION_FAILED);
    }

    @Test
    @DisplayName("issue 5MB 초과_거부")
    void issue_5MB_초과_거부() {
        assertThatThrownBy(() -> service.issue(FilePurpose.ITEM, OWNER, List.of(
                new PresignRequestItem("image/jpeg", 5L * 1024 * 1024 + 1)
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_VALIDATION_FAILED);
    }

    @Test
    @DisplayName("issue 5MB 정확히는 허용")
    void issue_5MB_정확_허용() {
        List<PresignResult> r = service.issue(FilePurpose.ITEM, OWNER, List.of(
                new PresignRequestItem("image/jpeg", 5L * 1024 * 1024)
        ));
        assertThat(r).hasSize(1);
    }

    @Test
    @DisplayName("issue 0 또는 음수 size_거부")
    void issue_size_비양수_거부() {
        assertThatThrownBy(() -> service.issue(FilePurpose.ITEM, OWNER, List.of(
                new PresignRequestItem("image/jpeg", 0L)
        ))).isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> service.issue(FilePurpose.ITEM, OWNER, List.of(
                new PresignRequestItem("image/jpeg", -1L)
        ))).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("issue 빈 파일 리스트_INVALID_REQUEST")
    void issue_빈_리스트() {
        assertThatThrownBy(() -> service.issue(FilePurpose.ITEM, OWNER, List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("issue 파일 11개_FILE_VALIDATION_FAILED")
    void issue_파일초과() {
        List<PresignRequestItem> many = new ArrayList<>();
        for (int i = 0; i < 11; i++) many.add(jpeg());

        assertThatThrownBy(() -> service.issue(FilePurpose.ITEM, OWNER, many))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_VALIDATION_FAILED);
    }

    @Test
    @DisplayName("issue null purpose / ownerId_INVALID_REQUEST")
    void issue_null_파라미터() {
        assertThatThrownBy(() -> service.issue(null, OWNER, List.of(jpeg())))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.issue(FilePurpose.ITEM, null, List.of(jpeg())))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.issue(FilePurpose.ITEM, 0L, List.of(jpeg())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("issueForUser PROFILE/ITEM/SUPPORT/ESCROW/MESSAGE 허용")
    void issueForUser_화이트리스트() {
        assertThat(service.issueForUser(FilePurpose.PROFILE, OWNER, List.of(jpeg()))).hasSize(1);
        assertThat(service.issueForUser(FilePurpose.ITEM, OWNER, List.of(jpeg()))).hasSize(1);
        assertThat(service.issueForUser(FilePurpose.SUPPORT, OWNER, List.of(jpeg()))).hasSize(1);
        assertThat(service.issueForUser(FilePurpose.ESCROW, OWNER, List.of(jpeg()))).hasSize(1);
        assertThat(service.issueForUser(FilePurpose.MESSAGE, OWNER, List.of(jpeg()))).hasSize(1);
    }

    @Test
    @DisplayName("issueForUser NOTICE/BANNER_FORBIDDEN (관리자 도메인 전용)")
    void issueForUser_관리자_도메인_거부() {
        assertThatThrownBy(() -> service.issueForUser(FilePurpose.NOTICE, OWNER, List.of(jpeg())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> service.issueForUser(FilePurpose.BANNER, OWNER, List.of(jpeg())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("issueForUser null purpose_INVALID_REQUEST")
    void issueForUser_null() {
        assertThatThrownBy(() -> service.issueForUser(null, OWNER, List.of(jpeg())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    private static PresignRequestItem jpeg() {
        return new PresignRequestItem("image/jpeg", 1024L);
    }
}
