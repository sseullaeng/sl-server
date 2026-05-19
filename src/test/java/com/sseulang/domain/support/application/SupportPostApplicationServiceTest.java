package com.sseulang.domain.support.application;

import com.sseulang.domain.support.application.dto.SupportPostResult;
import com.sseulang.domain.support.application.dto.SupportPostUpsertCommand;
import com.sseulang.domain.support.domain.InquiryCategory;
import com.sseulang.domain.support.domain.SupportPostType;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportPostApplicationServiceTest {

    private static final Long ADMIN = 1L;

    private InMemoryFakeSupportPostRepository repo;
    private SupportPostApplicationService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryFakeSupportPostRepository();
        service = new SupportPostApplicationService(repo);
    }

    private SupportPostUpsertCommand cmd(SupportPostType type, InquiryCategory cat) {
        return new SupportPostUpsertCommand(type, cat, "질문?", "답변.", List.of());
    }

    @Test
    @DisplayName("create_정상 저장")
    void create_정상() {
        Long id = service.create(ADMIN, cmd(SupportPostType.FAQ, InquiryCategory.결제));

        SupportPostResult r = service.findById(id);
        assertThat(r.postType()).isEqualTo(SupportPostType.FAQ);
        assertThat(r.category()).isEqualTo(InquiryCategory.결제);
    }

    @Test
    @DisplayName("update_정상_필드 갱신")
    void update_정상() {
        Long id = service.create(ADMIN, cmd(SupportPostType.FAQ, InquiryCategory.결제));

        service.update(id, new SupportPostUpsertCommand(
                SupportPostType.QNA, InquiryCategory.거래, "변경 질문", "변경 답변", List.of()
        ));

        SupportPostResult r = service.findById(id);
        assertThat(r.postType()).isEqualTo(SupportPostType.QNA);
        assertThat(r.category()).isEqualTo(InquiryCategory.거래);
        assertThat(r.question()).isEqualTo("변경 질문");
    }

    @Test
    @DisplayName("delete_미존재_SUPPORT_POST_NOT_FOUND")
    void delete_미존재_404() {
        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SUPPORT_POST_NOT_FOUND);
    }

    @Test
    @DisplayName("findVisible_type 매칭만 반환_category 필터 없음")
    void findVisible_type_필터() {
        service.create(ADMIN, cmd(SupportPostType.FAQ, InquiryCategory.결제));
        service.create(ADMIN, cmd(SupportPostType.FAQ, InquiryCategory.배송));
        service.create(ADMIN, cmd(SupportPostType.QNA, InquiryCategory.결제));

        var faqPage = service.findVisible(SupportPostType.FAQ, null, PageRequest.of(0, 10));
        assertThat(faqPage.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("findVisible_category 추가 필터")
    void findVisible_type_category() {
        service.create(ADMIN, cmd(SupportPostType.FAQ, InquiryCategory.결제));
        service.create(ADMIN, cmd(SupportPostType.FAQ, InquiryCategory.배송));

        var page = service.findVisible(SupportPostType.FAQ, InquiryCategory.결제, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
    }
}
