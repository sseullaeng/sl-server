package com.sseulang.domain.support.application;

import com.sseulang.domain.support.application.dto.InquiryCreateCommand;
import com.sseulang.domain.support.application.dto.InquiryReplyCommand;
import com.sseulang.domain.support.application.dto.InquiryResult;
import com.sseulang.domain.support.domain.Inquiry;
import com.sseulang.domain.support.domain.InquiryRepository;
import com.sseulang.domain.support.domain.InquiryStatus;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 1:1 문의 흐름.
 *
 * <ul>
 *   <li>사용자: 작성 / 본인 목록·단건 조회 / PENDING 상태일 때만 본인 삭제.</li>
 *   <li>관리자: 전체 조회 / 답변 작성 (status 동시 갱신) / status 변경 / 삭제.</li>
 * </ul>
 *
 * <p>관리자 권한 강제는 SecurityConfig admin chain ({@code hasRole("ADMIN")}). 본 서비스는
 * 사용자 권한 검증만 — 본인 문의가 아니면 {@link ErrorCode#INQUIRY_FORBIDDEN}.</p>
 */
@Service
@Transactional(readOnly = true)
public class InquiryApplicationService {

    private final InquiryRepository inquiryRepository;
    private final Clock clock;

    public InquiryApplicationService(InquiryRepository inquiryRepository, Clock clock) {
        this.inquiryRepository = inquiryRepository;
        this.clock = clock;
    }

    @Transactional
    public Long create(Long userId, InquiryCreateCommand cmd) {
        Inquiry inquiry = Inquiry.create(
                userId,
                cmd.category(),
                cmd.title(),
                cmd.content(),
                cmd.email(),
                cmd.imageUrls()
        );
        return inquiryRepository.save(inquiry).getId();
    }

    public InquiryResult findOwnedById(Long inquiryId, Long viewerId) {
        Inquiry inquiry = findOrThrow(inquiryId);
        if (!inquiry.isOwnedBy(viewerId)) {
            // 존재 leak 방지를 위해 NOT_FOUND 도 고려했으나 명세상 INQUIRY_FORBIDDEN 으로 분리.
            throw new BusinessException(ErrorCode.INQUIRY_FORBIDDEN);
        }
        return InquiryResult.from(inquiry);
    }

    public Page<InquiryResult> findMine(Long userId, InquiryStatus status, Pageable pageable) {
        return inquiryRepository.findByUserId(userId, status, pageable).map(InquiryResult::from);
    }

    @Transactional
    public void deleteByOwner(Long inquiryId, Long viewerId) {
        Inquiry inquiry = findOrThrow(inquiryId);
        if (!inquiry.isOwnedBy(viewerId)) {
            throw new BusinessException(ErrorCode.INQUIRY_FORBIDDEN);
        }
        if (!inquiry.isDeletableByOwner()) {
            throw new BusinessException(ErrorCode.INQUIRY_INVALID_STATE);
        }
        inquiryRepository.deleteById(inquiryId);
    }

    // ===== 관리자 영역 =====

    public Page<InquiryResult> adminFindAll(InquiryStatus status, Pageable pageable) {
        return inquiryRepository.findAllForAdmin(status, pageable).map(InquiryResult::from);
    }

    public InquiryResult adminFindById(Long inquiryId) {
        return InquiryResult.from(findOrThrow(inquiryId));
    }

    /**
     * 답변 작성. status null 이면 DONE 으로 자동 — 명세대로.
     * 빈 문자열은 도메인에서 reject.
     */
    @Transactional
    public void adminReply(Long inquiryId, InquiryReplyCommand cmd) {
        Inquiry inquiry = findOrThrow(inquiryId);
        InquiryStatus next = cmd.status() == null ? InquiryStatus.DONE : cmd.status();
        inquiry.writeAdminReply(cmd.adminReply(), next, LocalDateTime.now(clock));
    }

    @Transactional
    public void adminChangeStatus(Long inquiryId, InquiryStatus newStatus) {
        Inquiry inquiry = findOrThrow(inquiryId);
        inquiry.changeStatus(newStatus);
    }

    @Transactional
    public void adminDelete(Long inquiryId) {
        if (inquiryRepository.findById(inquiryId).isEmpty()) {
            throw new BusinessException(ErrorCode.INQUIRY_NOT_FOUND);
        }
        inquiryRepository.deleteById(inquiryId);
    }

    private Inquiry findOrThrow(Long id) {
        return inquiryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
    }
}
