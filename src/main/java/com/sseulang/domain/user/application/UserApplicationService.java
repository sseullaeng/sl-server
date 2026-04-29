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

    /**
     * 가이드 §4.7 — Review 작성 시점에 호출. review_count / rating_sum 누적 + trust_score 재계산.
     * 단일 native UPDATE 라 동시 review 작성 race 안전 (Codex 게이트 2 보강 — 기존 AVG 서브쿼리
     * 방식의 REPEATABLE_READ stale view 문제 차단). Review 도메인은 본 메서드만 의존
     * (UserRepository 직접 호출 금지, CLAUDE.md §3.3).
     */
    @Transactional
    public void recordReview(Long revieweeId, int rating) {
        userRepository.recordReviewFor(revieweeId, rating);
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
