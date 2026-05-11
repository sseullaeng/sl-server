package com.sseulang.domain.user.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 활동 정지 누적 200일 이상 도달 사용자 자동 탈퇴 배치 (라운드 12 PR-F #8).
 *
 * <p>1차 트리거는 {@code UserApplicationService.adminSuspend} 내부 동기 분기 — 보통은 거기서 처리된다.
 * 본 스케줄러는 안전망: DB 수동 수정, 마이그레이션 backfill, 정책 변경 등으로 누락된 사용자를 매일 새벽 1시에
 * 청소한다.</p>
 *
 * <p>각 사용자는 별도 트랜잭션으로 처리 — 한 건 실패해도 다음 건 진행. 메일 발송 실패는 swallow.</p>
 */
@Component
public class AutoWithdrawalScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoWithdrawalScheduler.class);

    /** 한 cycle 에 처리할 최대 건수 — 대량 발생 시 다음 cycle 로 자연 분산. */
    private static final int BATCH_LIMIT = 100;

    private final UserApplicationService userService;

    public AutoWithdrawalScheduler(UserApplicationService userService) {
        this.userService = userService;
    }

    /** 매일 새벽 1시 KST. */
    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    public void run() {
        List<Long> targets = userService.findAutoWithdrawTargetIds(BATCH_LIMIT);
        if (targets.isEmpty()) {
            return;
        }
        log.info("[auto-withdraw] target {} 명 처리 시작", targets.size());
        int processed = 0;
        for (Long id : targets) {
            try {
                if (userService.processAutoWithdrawal(id)) {
                    processed++;
                }
            } catch (Exception e) {
                log.error("[auto-withdraw] userId={} 처리 실패 (다음 cycle 재시도)", id, e);
            }
        }
        log.info("[auto-withdraw] target {} 명 중 {} 명 탈퇴 처리", targets.size(), processed);
    }
}
