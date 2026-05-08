package com.sseulang.domain.admin.application;

import com.sseulang.domain.admin.domain.Admin;
import com.sseulang.domain.admin.domain.AdminRepository;
import com.sseulang.domain.admin.presentation.dto.AdminMeResponse;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin 본인 정보 조회용 read-only 서비스. 로그인 흐름은 {@link AdminLoginService} 가 담당.
 *
 * <p><b>admin 권한 두 출처 모두 처리</b>:
 * <ul>
 *   <li>{@code admins} 테이블 — username/password 로 로그인한 admin (subject=admin.id)</li>
 *   <li>{@code users} 테이블 — OAuth allowlist 매치 사용자 ({@code app.admin.user-emails}, subject=user.id)</li>
 * </ul>
 * SecurityConfig admin chain 은 JWT role=ADMIN 만 검증하므로 두 출처 모두 같은 chain 통과.
 * 본 서비스가 subject id 로 두 테이블 순차 조회 — admins 우선, 없으면 users fallback.</p>
 */
@Service
@Transactional(readOnly = true)
public class AdminApplicationService {

    private final AdminRepository adminRepository;
    private final UserApplicationService userService;

    public AdminApplicationService(
            AdminRepository adminRepository,
            UserApplicationService userService
    ) {
        this.adminRepository = adminRepository;
        this.userService = userService;
    }

    /**
     * subject id 로 admin 본인 정보 조회. admins 테이블 우선, 없으면 OAuth allowlist 매치 user 로 fallback.
     * 양쪽 모두 미존재 시 USER_NOT_FOUND.
     */
    public AdminMeResponse getMe(Long subjectId) {
        Admin admin = adminRepository.findById(subjectId).orElse(null);
        if (admin != null) {
            return AdminMeResponse.from(admin);
        }
        // OAuth allowlist 매치 admin — users 테이블 fallback. UserApplicationService.getById 가
        // 미존재 시 USER_NOT_FOUND throw 라 양쪽 fail 시 동일 에러로 노출.
        var user = userService.getById(subjectId);
        return AdminMeResponse.fromUser(user);
    }
}
