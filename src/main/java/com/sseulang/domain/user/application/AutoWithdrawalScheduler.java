package com.sseulang.domain.user.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AutoWithdrawalScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoWithdrawalScheduler.class);

    
    private static final int BATCH_LIMIT = 100;

    private final UserApplicationService userService;

    public AutoWithdrawalScheduler(UserApplicationService userService) {
        this.userService = userService;
    }

    
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
