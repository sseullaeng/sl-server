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

import java.util.Optional;

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
            // 본 catch 는 UNIQUE 충돌 race 만 보정. 다른 제약(닉네임 길이 등) 은 그대로 던져
            // 시스템 에러로 처리한다 — 사용자에게 잘못된 USER_EMAIL_DUPLICATED 응답 방지.
            return resolveRaceOrRethrow(provider, providerId, email, race);
        }
    }

    private User resolveRaceOrRethrow(
            SocialProvider provider, String providerId, Email email, DataIntegrityViolationException race
    ) {
        // race winner 가 같은 (provider, providerId) 면 그것을 반환
        Optional<User> raceWinner = userRepository.findBySocial(provider, providerId);
        if (raceWinner.isPresent()) {
            return raceWinner.get();
        }
        // 다른 user 가 같은 email 로 가입한 경우만 USER_EMAIL_DUPLICATED 로 변환
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(ErrorCode.USER_EMAIL_DUPLICATED);
        }
        // UNIQUE 충돌이 아닌 다른 제약 위반 — 시스템 에러로 그대로 노출
        throw race;
    }
}
