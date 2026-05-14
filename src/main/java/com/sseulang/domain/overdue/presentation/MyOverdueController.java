package com.sseulang.domain.overdue.presentation;

import com.sseulang.domain.overdue.application.OverdueApplicationService;
import com.sseulang.domain.overdue.domain.OverdueStatus;
import com.sseulang.domain.overdue.presentation.dto.UserOverdueDebtResponse;
import com.sseulang.domain.overdue.presentation.dto.UserOverdueResponse;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "MyOverdue", description = "본인 연체 정보 조회")
@RestController
@RequestMapping("/api/v1/users/me")
public class MyOverdueController {

    private final OverdueApplicationService overdueService;
    private final UserApplicationService userApplicationService;

    public MyOverdueController(
            OverdueApplicationService overdueService,
            UserApplicationService userApplicationService
    ) {
        this.overdueService = overdueService;
        this.userApplicationService = userApplicationService;
    }

    @Operation(summary = "[본인] 진행/정산완료/법적조치중 연체 record 목록",
            description = "종료 상태는 제외. 최근순. buyer 본인만 조회.")
    @GetMapping("/overdue")
    public ApiResponse<List<UserOverdueResponse>> myOverdue(@AuthenticationPrincipal Long userId) {
        List<OverdueStatus> active = List.of(
                OverdueStatus.진행중,
                OverdueStatus.정산완료,
                OverdueStatus.법적조치중
        );
        return ApiResponse.ok(
                overdueService.findByBuyer(userId, active).stream()
                        .map(UserOverdueResponse::from)
                        .toList()
        );
    }

    @Operation(summary = "[본인] 누적 연체 채무 잔액",
            description = "users.overdue_debt_balance 단순 조회. 다음 충전 시 우선 차감.")
    @GetMapping("/overdue-debt")
    public ApiResponse<UserOverdueDebtResponse> myOverdueDebt(@AuthenticationPrincipal Long userId) {
        long debt = userApplicationService.findOverdueDebt(userId);
        return ApiResponse.ok(new UserOverdueDebtResponse(debt));
    }
}
