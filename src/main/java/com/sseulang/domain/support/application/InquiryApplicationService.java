package com.sseulang.domain.support.application;

import com.sseulang.domain.auth.domain.EmailSender;
import com.sseulang.domain.notification.application.NotificationApplicationService;
import com.sseulang.domain.notification.domain.NotificationType;
import com.sseulang.domain.support.application.dto.InquiryCreateCommand;
import com.sseulang.domain.support.application.dto.InquiryReplyCommand;
import com.sseulang.domain.support.application.dto.InquiryResult;
import com.sseulang.domain.support.domain.Inquiry;
import com.sseulang.domain.support.domain.InquiryRepository;
import com.sseulang.domain.support.domain.InquiryStatus;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@Transactional(readOnly = true)
public class InquiryApplicationService {

    private final InquiryRepository inquiryRepository;
    private final NotificationApplicationService notificationService;
    private final EmailSender emailSender;
    private final Clock clock;

    public InquiryApplicationService(
            InquiryRepository inquiryRepository,
            NotificationApplicationService notificationService,
            EmailSender emailSender,
            Clock clock
    ) {
        this.inquiryRepository = inquiryRepository;
        this.notificationService = notificationService;
        this.emailSender = emailSender;
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
     *
     * <p><b>알림 흐름</b> (round 9):
     * <ol>
     *   <li>Notification INSERT — 사용자가 SideDrawer / NotificationPage 에서 즉시 확인</li>
     *   <li>Email 발송 — best-effort. SMTP 실패해도 트랜잭션 롤백 X (답변 자체는 저장됨).
     *       예외는 WARN 로그만, 사용자에게 영향 없음.</li>
     * </ol>
     * 메일 발송이 트랜잭션 안에 있어 SMTP latency 가 응답 시간에 포함됨 — 5/6 이후 outbox 패턴
     * 도입 시 비동기로 전환 (현재는 단순화 우선).</p>
     */
    @Transactional
    public void adminReply(Long inquiryId, InquiryReplyCommand cmd) {
        Inquiry inquiry = findOrThrow(inquiryId);
        InquiryStatus next = cmd.status() == null ? InquiryStatus.DONE : cmd.status();
        inquiry.writeAdminReply(cmd.adminReply(), next, LocalDateTime.now(clock));

        // 1) Notification — 푸시 알림 (SideDrawer)
        notificationService.notify(
                inquiry.getUserId(),
                NotificationType.시스템,
                "문의 답변이 도착했어요",
                inquiry.getTitle(),
                "inquiry",
                inquiry.getId()
        );

        // 2) Email — best-effort. SMTP 실패해도 답변/알림은 보존.
        try {
            emailSender.sendInquiryReplyEmail(
                    inquiry.getEmail(),
                    "[쓸랭] 문의 답변이 도착했습니다",
                    buildReplyEmailHtml(inquiry, cmd.adminReply())
            );
        } catch (RuntimeException e) {
            log.warn("Inquiry reply email 발송 실패 — inquiryId={}, email={}, cause={}",
                    inquiry.getId(), inquiry.getEmail(), e.getMessage());
        }
    }

    private static String buildReplyEmailHtml(Inquiry inquiry, String adminReply) {
        return """
                <!doctype html>
                <html>
                  <body style="font-family:sans-serif;line-height:1.6;color:#333">
                    <h2>쓸랭 문의 답변</h2>
                    <p><b>문의 제목</b><br>%s</p>
                    <hr>
                    <p><b>답변</b></p>
                    <pre style="white-space:pre-wrap;font-family:inherit">%s</pre>
                    <p style="color:#888;font-size:12px">마이페이지 &gt; 1:1 문의에서도 확인하실 수 있어요.</p>
                  </body>
                </html>
                """.formatted(escapeHtml(inquiry.getTitle()), escapeHtml(adminReply));
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
