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

@Slf4j
@Service
@Transactional(readOnly = true)
public class InquiryApplicationService {

    private final InquiryRepository inquiryRepository;
    private final NotificationApplicationService notificationService;
    private final EmailSender emailSender;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public InquiryApplicationService(
            InquiryRepository inquiryRepository,
            NotificationApplicationService notificationService,
            EmailSender emailSender,
            org.springframework.context.ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.inquiryRepository = inquiryRepository;
        this.notificationService = notificationService;
        this.emailSender = emailSender;
        this.eventPublisher = eventPublisher;
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

    

    public Page<InquiryResult> adminFindAll(InquiryStatus status, Pageable pageable) {
        return inquiryRepository.findAllForAdmin(status, pageable).map(InquiryResult::from);
    }

    public InquiryResult adminFindById(Long inquiryId) {
        return InquiryResult.from(findOrThrow(inquiryId));
    }

    

    @Transactional
    public void adminReply(Long inquiryId, InquiryReplyCommand cmd) {
        if (cmd.status() == InquiryStatus.PENDING) {
            throw new BusinessException(ErrorCode.INQUIRY_INVALID_STATE);
        }
        Inquiry inquiry = findOrThrow(inquiryId);
        InquiryStatus next = cmd.status() == null ? InquiryStatus.DONE : cmd.status();
        try {
            inquiry.writeAdminReply(cmd.adminReply(), next, LocalDateTime.now(clock));
        } catch (IllegalArgumentException e) {
            
            throw new BusinessException(ErrorCode.INQUIRY_INVALID_STATE);
        }
        
        eventPublisher.publishEvent(new InquiryRepliedEvent(
                inquiry.getId(), inquiry.getUserId(), inquiry.getEmail(),
                inquiry.getTitle(), cmd.adminReply()
        ));
    }

    
    @org.springframework.transaction.event.TransactionalEventListener(
            phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void onInquiryReplied(InquiryRepliedEvent event) {
        notificationService.notify(
                event.userId(), NotificationType.시스템,
                "문의 답변이 도착했어요", event.title(),
                "inquiry", event.inquiryId()
        );
        try {
            emailSender.sendInquiryReplyEmail(
                    event.email(),
                    "[쓸랭] 문의 답변이 도착했습니다",
                    buildReplyEmailHtml(event.title(), event.adminReply())
            );
        } catch (RuntimeException e) {
            log.warn("Inquiry reply email 발송 실패 — inquiryId={}, email={}, cause={}",
                    event.inquiryId(), event.email(), e.getMessage());
        }
    }

    
    public record InquiryRepliedEvent(Long inquiryId, Long userId, String email, String title, String adminReply) { }

    private static String buildReplyEmailHtml(String title, String adminReply) {
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
                """.formatted(escapeHtml(title), escapeHtml(adminReply));
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Transactional
    public void adminChangeStatus(Long inquiryId, InquiryStatus newStatus) {
        Inquiry inquiry = findOrThrow(inquiryId);
        try {
            inquiry.changeStatus(newStatus);
        } catch (IllegalArgumentException e) {
            
            throw new BusinessException(ErrorCode.INQUIRY_INVALID_STATE);
        }
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
