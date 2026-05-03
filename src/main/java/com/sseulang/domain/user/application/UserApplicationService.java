package com.sseulang.domain.user.application;

import com.sseulang.domain.user.application.dto.UserStatsResult;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.user.domain.UserRepository;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserApplicationService {

    /** 휴면 판정 — 90일 이상 미접속이면 dormant. status derive 와 search 양쪽에서 공유. */
    private static final int DORMANT_THRESHOLD_DAYS = 90;

    private final UserRepository userRepository;
    private final com.sseulang.domain.transaction.domain.TransactionRepository transactionRepository;
    private final com.sseulang.domain.report.domain.UserReportRepository userReportRepository;
    private final java.time.Clock clock;

    public UserApplicationService(
            UserRepository userRepository,
            com.sseulang.domain.transaction.domain.TransactionRepository transactionRepository,
            com.sseulang.domain.report.domain.UserReportRepository userReportRepository,
            java.time.Clock clock
    ) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.userReportRepository = userReportRepository;
        this.clock = clock;
    }

    /**
     * 소셜 가입/로그인 흐름 — (provider, providerId) 로 기존 user 조회. 없으면:
     * <ol>
     *   <li>같은 email 의 기존 user 가 있으면:
     *     <ul>
     *       <li>LOCAL 가입자 → <b>takeover</b>: linkSocial(provider, providerId), 기존 password 무효화 +
     *           verified=true. 이메일 선점 공격 무력화 (게이트 1).</li>
     *       <li>다른 provider OAuth 가입자 → AUTH_EMAIL_ALREADY_LINKED_TO_DIFFERENT_PROVIDER 거부</li>
     *     </ul>
     *   </li>
     *   <li>같은 email user 없음 → 신규 OAuth user 생성 (verified=true).</li>
     * </ol>
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
                .orElseGet(() -> linkOrCreate(provider, providerId, email, nickname, profileImage));
    }

    /**
     * 같은 email user 발견 분기 처리 (linking) + 없으면 신규 생성. {@code create} race 처리도 동일.
     */
    private User linkOrCreate(SocialProvider provider, String providerId, Email email, String nickname, String profileImage) {
        Optional<User> sameEmail = userRepository.findByEmail(email);
        if (sameEmail.isPresent()) {
            User existing = sameEmail.get();
            if (existing.getSocialProvider() == SocialProvider.LOCAL) {
                // LOCAL 가입자 → OAuth takeover. 기존 password 무효화, social 정보 추가.
                existing.linkSocial(provider, providerId);
                return existing;
            }
            // 다른 OAuth provider — 본 PR scope 에선 multi-provider linking 미지원.
            throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_LINKED_TO_DIFFERENT_PROVIDER);
        }
        return create(provider, providerId, email, nickname, profileImage);
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 본인 프로필 partial update — null 필드는 변경 X. 미인증 사용자도 호출 가능 (자금/거래 영향 0).
     * profileImage 빈 문자열 = 이미지 제거.
     */
    @Transactional
    public User updateProfile(Long userId, String profileImage, String nickname) {
        User user = getById(userId);
        user.updateProfile(profileImage, nickname);
        return user;
    }

    /**
     * 민감 기능 진입 가드 — 이메일 인증 미완료 시 {@link ErrorCode#AUTH_EMAIL_NOT_VERIFIED} (FORBIDDEN).
     * 거래 시작 / 결제 / 출금 / Item 등록 등 자금·신뢰 영향 흐름의 첫 진입점에서 호출.
     */
    public void requireVerified(Long userId) {
        User user = getById(userId);
        if (!user.isEmailVerified()) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
        }
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

    /**
     * 가이드 §4.8 — point_balance atomic 증가. 충전(Day 7) / 거래 정산 적립(Day 8) 호출.
     * Payment 도메인은 본 메서드만 의존 (UserRepository 직접 호출 금지, CLAUDE.md §3.3).
     * amount 는 양수 강제. UPDATE 영향 행 0 건 → USER_NOT_FOUND 로 트랜잭션 롤백
     * (Codex 게이트 1 보강 — 결제 완료 상태로 전이됐는데 포인트 미적립 dangling 차단).
     */
    @Transactional
    public void creditPoint(Long userId, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다");
        }
        int affected = userRepository.creditPointBalance(userId, amount);
        if (affected != 1) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    /**
     * 가이드 §4.8 / §5.3 — point_balance atomic 차감. 거래 결제 (구매자) / 출금 신청에서 호출.
     * 잔액 부족 시 affected=0 → INSUFFICIENT_POINT 로 트랜잭션 롤백 (음수 방지 가드).
     * Point 도메인은 본 메서드만 의존 (UserRepository 직접 호출 금지, CLAUDE.md §3.3).
     */
    @Transactional
    public void deductPoint(Long userId, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다");
        }
        int affected = userRepository.deductPointBalance(userId, amount);
        if (affected != 1) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINT);
        }
    }

    /**
     * 잔액 단건 조회 — atomic UPDATE 직후 balance_after 적재용. native scalar 라 영속성 컨텍스트
     * stale 우회. 미존재 userId 시 USER_NOT_FOUND.
     */
    public long getPointBalance(Long userId) {
        Long balance = userRepository.findPointBalance(userId);
        if (balance == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return balance;
    }

    /**
     * 관리자 회원 목록 — created_at DESC 페이징. 차단/삭제 상태 모두 포함.
     */
    public Page<User> adminFindAll(Pageable pageable) {
        return userRepository.findAllForAdmin(pageable);
    }

    /**
     * Admin 회원 검색 + enrich. status/keyword/created_at 범위 필터 후 페이지의 userIds 로 batch
     * tradeCount / reportCount 집계 (N+1 회피).
     */
    public Page<com.sseulang.domain.user.application.dto.AdminUserResult> adminSearch(
            com.sseulang.domain.user.application.dto.AdminUserSearchCriteria criteria,
            Pageable pageable
    ) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now(clock);
        Page<User> page = userRepository.searchForAdmin(criteria, now, DORMANT_THRESHOLD_DAYS, pageable);
        if (page.isEmpty()) {
            return page.map(u -> com.sseulang.domain.user.application.dto.AdminUserResult.from(
                    u, now, DORMANT_THRESHOLD_DAYS, 0L, 0L));
        }
        java.util.List<Long> ids = page.getContent().stream().map(User::getId).toList();
        java.util.Map<Long, Long> tradeCounts = transactionRepository.countByUserIdsAsParticipant(ids);
        java.util.Map<Long, Long> reportCounts = userReportRepository.countByTargetUserIds(ids);
        return page.map(u -> com.sseulang.domain.user.application.dto.AdminUserResult.from(
                u, now, DORMANT_THRESHOLD_DAYS,
                tradeCounts.getOrDefault(u.getId(), 0L),
                reportCounts.getOrDefault(u.getId(), 0L)
        ));
    }

    /** 단건 enrich — 관리자 단건 조회. */
    public com.sseulang.domain.user.application.dto.AdminUserResult adminGetEnriched(Long userId) {
        User u = getById(userId);
        java.time.LocalDateTime now = java.time.LocalDateTime.now(clock);
        java.util.List<Long> ids = java.util.List.of(userId);
        long trades = transactionRepository.countByUserIdsAsParticipant(ids).getOrDefault(userId, 0L);
        long reports = userReportRepository.countByTargetUserIds(ids).getOrDefault(userId, 0L);
        return com.sseulang.domain.user.application.dto.AdminUserResult.from(u, now, DORMANT_THRESHOLD_DAYS, trades, reports);
    }

    /**
     * 관리자 차단/해제 — Aggregate {@code block()/unblock()} 위임. 미존재 userId → USER_NOT_FOUND.
     * 멱등 (이미 차단된 사용자를 또 차단해도 OK).
     */
    @Transactional
    public void adminSetBlocked(Long userId, boolean blocked) {
        User u = getById(userId);
        if (blocked) u.block(); else u.unblock();
    }

    /** 관리자 시한부 활동정지 — N일 동안. days >= 1. */
    @Transactional
    public void adminSuspend(Long userId, int days) {
        User u = getById(userId);
        u.suspend(days, java.time.LocalDateTime.now(clock));
    }

    /** 관리자 활동정지 즉시 해제. */
    @Transactional
    public void adminUnsuspend(Long userId) {
        User u = getById(userId);
        u.unsuspend();
    }

    /** 로그인 성공 직후 호출 — 마지막 로그인 시각 기록 (휴면 판정 기준). 미존재 userId 무시. */
    @Transactional
    public void recordLogin(Long userId) {
        userRepository.findById(userId).ifPresent(u -> u.recordLogin(java.time.LocalDateTime.now(clock)));
    }

    /**
     * 관리자 회원 통계 — total / blocked / deleted / active 모두 직접 집계.
     * active 는 별도 쿼리(`WHERE blocked=false AND deleted=false`) 로 정확 — 이중 차감(blocked &&
     * deleted) 시나리오에서도 정확 (게이트 2 보강).
     * stats 도메인이 본 메서드만 의존하도록 하여 UserRepository 직접 호출 차단 (CLAUDE.md §3.3).
     */
    public UserStatsResult adminGetStats() {
        long total = userRepository.countAll();
        long blocked = userRepository.countBlocked();
        long deleted = userRepository.countDeleted();
        long active = userRepository.countActive();
        return new UserStatsResult(total, blocked, deleted, active);
    }

    private User create(SocialProvider provider, String providerId, Email email, String nickname, String profileImage) {
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
        // 다른 user 가 같은 email 로 가입했다면 linking 흐름 재진입 (takeover or 거부)
        Optional<User> sameEmail = userRepository.findByEmail(email);
        if (sameEmail.isPresent()) {
            User existing = sameEmail.get();
            if (existing.getSocialProvider() == SocialProvider.LOCAL) {
                existing.linkSocial(provider, providerId);
                return existing;
            }
            throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_LINKED_TO_DIFFERENT_PROVIDER);
        }
        // UNIQUE 충돌이 아닌 다른 제약 위반 — 시스템 에러로 그대로 노출
        throw race;
    }
}
