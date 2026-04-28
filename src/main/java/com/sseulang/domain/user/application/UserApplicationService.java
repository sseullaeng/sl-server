package com.sseulang.domain.user.application;

import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.user.domain.UserRepository;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserApplicationService {

    private final UserRepository userRepository;

    public UserApplicationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 소셜 가입 흐름 — (provider, providerId) 로 기존 user 조회, 없으면 신규 가입.
     *
     * <p>주의: 같은 email 이 다른 provider 로 이미 가입돼 있으면 {@code USER_EMAIL_DUPLICATED}.
     * 이 정책은 가이드 §12 의 미결정 영역 — 추후 PM 협의로 "동일 이메일 다중 provider 연결" 허용 시 변경.</p>
     */
    @Transactional
    public User findOrCreateBySocial(
            SocialProvider provider,
            String providerId,
            Email email,
            String nickname,
            String profileImage
    ) {
        return userRepository.findBySocial(provider, providerId)
                .orElseGet(() -> create(provider, providerId, email, nickname, profileImage));
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private User create(SocialProvider provider, String providerId, Email email, String nickname, String profileImage) {
        userRepository.findByEmail(email).ifPresent(existing -> {
            throw new BusinessException(ErrorCode.USER_EMAIL_DUPLICATED);
        });
        User newUser = User.createSocialUser(provider, providerId, email, nickname, profileImage);
        try {
            return userRepository.save(newUser);
        } catch (DataIntegrityViolationException race) {
            // 동시 호출 race — DB UNIQUE 제약(email 또는 social_provider+social_id)에 걸림.
            // race winner 가 같은 (provider, providerId) 면 그것을 반환, 아니면 email 충돌.
            return userRepository.findBySocial(provider, providerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_EMAIL_DUPLICATED));
        }
    }
}
