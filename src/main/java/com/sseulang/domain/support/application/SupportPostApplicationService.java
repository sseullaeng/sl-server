package com.sseulang.domain.support.application;

import com.sseulang.domain.support.application.dto.SupportPostResult;
import com.sseulang.domain.support.application.dto.SupportPostUpsertCommand;
import com.sseulang.domain.support.domain.InquiryCategory;
import com.sseulang.domain.support.domain.SupportPost;
import com.sseulang.domain.support.domain.SupportPostRepository;
import com.sseulang.domain.support.domain.SupportPostType;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SupportPostApplicationService {

    private final SupportPostRepository repository;

    public SupportPostApplicationService(SupportPostRepository repository) {
        this.repository = repository;
    }

    public Page<SupportPostResult> findVisible(SupportPostType type, InquiryCategory category, Pageable pageable) {
        return repository.findVisible(type, category, pageable).map(SupportPostResult::from);
    }

    public SupportPostResult findById(Long id) {
        return SupportPostResult.from(findOrThrow(id));
    }

    @Transactional
    public Long create(Long adminId, SupportPostUpsertCommand cmd) {
        SupportPost post = SupportPost.create(
                adminId,
                cmd.postType(),
                cmd.category(),
                cmd.question(),
                cmd.answer(),
                cmd.imageUrls()
        );
        return repository.save(post).getId();
    }

    @Transactional
    public void update(Long id, SupportPostUpsertCommand cmd) {
        SupportPost post = findOrThrow(id);
        post.update(cmd.postType(), cmd.category(), cmd.question(), cmd.answer(), cmd.imageUrls());
    }

    @Transactional
    public void delete(Long id) {
        if (repository.findById(id).isEmpty()) {
            throw new BusinessException(ErrorCode.SUPPORT_POST_NOT_FOUND);
        }
        repository.deleteById(id);
    }

    private SupportPost findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_POST_NOT_FOUND));
    }
}
