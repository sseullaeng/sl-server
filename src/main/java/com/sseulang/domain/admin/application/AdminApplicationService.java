package com.sseulang.domain.admin.application;

import com.sseulang.domain.admin.domain.Admin;
import com.sseulang.domain.admin.domain.AdminRepository;
import com.sseulang.domain.admin.presentation.dto.AdminMeResponse;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    

    public AdminMeResponse getMe(Long subjectId) {
        Admin admin = adminRepository.findById(subjectId).orElse(null);
        if (admin != null) {
            return AdminMeResponse.from(admin);
        }
        
        
        var user = userService.getById(subjectId);
        return AdminMeResponse.fromUser(user);
    }
}
