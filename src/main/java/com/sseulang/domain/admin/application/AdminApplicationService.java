package com.sseulang.domain.admin.application;

import com.sseulang.domain.admin.domain.Admin;
import com.sseulang.domain.admin.domain.AdminRepository;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin 본인 정보 조회용 read-only 서비스. 로그인 흐름은 {@link AdminLoginService} 가 담당.
 *
 * <p>일반 사용자({@code users}) 와 별도 — admin chain ROLE_ADMIN 만 통과. 잔액/리뷰 등 사용자 자원 없음.</p>
 */
@Service
@Transactional(readOnly = true)
public class AdminApplicationService {

    private final AdminRepository adminRepository;

    public AdminApplicationService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    public Admin getById(Long adminId) {
        return adminRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
