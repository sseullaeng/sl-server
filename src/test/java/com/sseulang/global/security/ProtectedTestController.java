package com.sseulang.global.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuthSecurityFlowIT 전용 더미 controller. test 디렉토리에 위치하므로 production
 * 컨텍스트엔 영향 없음.
 */
@RestController
class ProtectedTestController {

    @GetMapping("/api/v1/test/protected")
    public String secret() {
        return "ok";
    }
}
