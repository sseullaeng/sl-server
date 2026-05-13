package com.sseulang.domain.user.application;

import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.user.domain.UserRepository;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserApplicationServiceTest {

    private static final Email EMAIL = new Email("foo@example.com");

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserApplicationService service;

    @Test
    @DisplayName("findOrCreateBySocial_기존 user 있음_save 호출 X")
    void findOrCreateBySocial_기존user() {
        User existing = User.createSocialUser(SocialProvider.KAKAO, "k-1", EMAIL, "n", null);
        when(userRepository.findBySocial(SocialProvider.KAKAO, "k-1")).thenReturn(Optional.of(existing));

        User result = service.findOrCreateBySocial(SocialProvider.KAKAO, "k-1", EMAIL, "ignored", null);

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    @DisplayName("findOrCreateBySocial_신규 + email 중복 없음_save 호출")
    void findOrCreateBySocial_신규() {
        when(userRepository.findBySocial(SocialProvider.GOOGLE, "g-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = service.findOrCreateBySocial(SocialProvider.GOOGLE, "g-1", EMAIL, "쓸랭이", "https://img/u.png");

        assertThat(result.getSocialProvider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(result.getSocialId()).isEqualTo("g-1");
        assertThat(result.getNickname()).isEqualTo("쓸랭이");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("findOrCreateBySocial_email 다른 OAuth provider 점유_AUTH_EMAIL_ALREADY_LINKED_TO_DIFFERENT_PROVIDER (linking 미지원)")
    void findOrCreateBySocial_email_다른_provider() {
        User other = User.createSocialUser(SocialProvider.KAKAO, "k-other", EMAIL, "n", null);
        when(userRepository.findBySocial(SocialProvider.GOOGLE, "g-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(other));

        assertThatThrownBy(() ->
                service.findOrCreateBySocial(SocialProvider.GOOGLE, "g-1", EMAIL, "n", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_EMAIL_ALREADY_LINKED_TO_DIFFERENT_PROVIDER);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreateBySocial_email 같은 미인증 LOCAL_takeover (squatting 방어, password null)")
    void findOrCreateBySocial_미인증_LOCAL_takeover() {
        // 공격자가 victim@email 로 LOCAL 가입 (verified=false). 진짜 owner 가 OAuth 가입 시도
        User local = User.createLocalUser(EMAIL, "$2a$10$attackerHash", "attacker");
        when(userRepository.findBySocial(SocialProvider.KAKAO, "k-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(local));

        User result = service.findOrCreateBySocial(SocialProvider.KAKAO, "k-1", EMAIL, "kakaoUser", null);

        assertThat(result).isSameAs(local);
        assertThat(result.getSocialProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(result.getSocialId()).isEqualTo("k-1");
        assertThat(result.getPassword()).as("기존 LOCAL password 무효화 — 공격자 차단").isNull();
        assertThat(result.isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("findOrCreateBySocial_email 같은 인증된 LOCAL_AUTH_OAUTH_LINK_REQUIRED")
    void findOrCreateBySocial_인증된_LOCAL_거부() {
        User local = User.createLocalUser(EMAIL, "$2a$10$hash", "owner");
        local.markEmailVerified();
        when(userRepository.findBySocial(SocialProvider.KAKAO, "k-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(local));

        assertThatThrownBy(() ->
                service.findOrCreateBySocial(SocialProvider.KAKAO, "k-1", EMAIL, "n", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_OAUTH_LINK_REQUIRED);

        assertThat(local.getSocialProvider()).isEqualTo(SocialProvider.LOCAL);
        assertThat(local.getSocialId()).isNull();
        assertThat(local.getPassword()).as("password 보존").isNotNull();
    }

    @Test
    @DisplayName("addSocialLink_정상_password 보존 + provider 세팅")
    void addSocialLink_정상() {
        User local = User.createLocalUser(EMAIL, "$2a$10$hash", "owner");
        local.markEmailVerified();
        when(userRepository.findById(7L)).thenReturn(Optional.of(local));
        when(userRepository.findBySocial(SocialProvider.KAKAO, "k-1")).thenReturn(Optional.empty());

        User result = service.addSocialLink(7L, SocialProvider.KAKAO, "k-1", EMAIL.value());

        assertThat(result.getSocialProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(result.getSocialId()).isEqualTo("k-1");
        assertThat(result.getPassword()).as("LOCAL password 유지 — 듀얼 로그인 가능").isNotNull();
    }

    @Test
    @DisplayName("addSocialLink_email 불일치_AUTH_OAUTH_LINK_EMAIL_MISMATCH")
    void addSocialLink_이메일불일치() {
        User local = User.createLocalUser(EMAIL, "$2a$10$hash", "owner");
        when(userRepository.findById(7L)).thenReturn(Optional.of(local));

        assertThatThrownBy(() ->
                service.addSocialLink(7L, SocialProvider.KAKAO, "k-1", "other@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_OAUTH_LINK_EMAIL_MISMATCH);
    }

    @Test
    @DisplayName("addSocialLink_이미 소셜 연결됨_AUTH_OAUTH_LINK_NOT_LOCAL")
    void addSocialLink_이미연결() {
        User social = User.createSocialUser(SocialProvider.GOOGLE, "g-1", EMAIL, "n", null);
        when(userRepository.findById(7L)).thenReturn(Optional.of(social));

        assertThatThrownBy(() ->
                service.addSocialLink(7L, SocialProvider.KAKAO, "k-1", EMAIL.value()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_OAUTH_LINK_NOT_LOCAL);
    }

    @Test
    @DisplayName("addSocialLink_socialId 가 다른 user 점유_AUTH_EMAIL_ALREADY_LINKED_TO_DIFFERENT_PROVIDER")
    void addSocialLink_소셜아이디_타사용자점유() {
        User local = User.createLocalUser(EMAIL, "$2a$10$hash", "owner");
        local.markEmailVerified();
        org.springframework.test.util.ReflectionTestUtils.setField(local, "id", 7L);
        User other = User.createSocialUser(SocialProvider.KAKAO, "k-1", new Email("other@example.com"), "x", null);
        org.springframework.test.util.ReflectionTestUtils.setField(other, "id", 9L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(local));
        when(userRepository.findBySocial(SocialProvider.KAKAO, "k-1")).thenReturn(Optional.of(other));

        assertThatThrownBy(() ->
                service.addSocialLink(7L, SocialProvider.KAKAO, "k-1", EMAIL.value()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_EMAIL_ALREADY_LINKED_TO_DIFFERENT_PROVIDER);
    }

    @Test
    @DisplayName("getById_존재_user 반환")
    void getById_존재() {
        User u = User.createSocialUser(SocialProvider.KAKAO, "k-1", EMAIL, "n", null);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));

        assertThat(service.getById(7L)).isSameAs(u);
    }

    @Test
    @DisplayName("getById_없음_USER_NOT_FOUND")
    void getById_없음() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("findOrCreateBySocial_save 시 DB UNIQUE 충돌_race winner 재조회 성공")
    void findOrCreateBySocial_race_winner_재조회() {
        // findBySocial: 처음엔 없음 → save 시 race winner 가 먼저 만들었음
        when(userRepository.findBySocial(SocialProvider.KAKAO, "k-1"))
                .thenReturn(Optional.empty())                                  // 첫 호출
                .thenReturn(Optional.of(                                       // race winner 가 만든 user
                        User.createSocialUser(SocialProvider.KAKAO, "k-1", EMAIL, "n", null)));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("UNIQUE 위반"));

        User result = service.findOrCreateBySocial(SocialProvider.KAKAO, "k-1", EMAIL, "n", null);

        assertThat(result.getSocialId()).isEqualTo("k-1");
        verify(userRepository, times(2)).findBySocial(SocialProvider.KAKAO, "k-1");
    }

    @Test
    @DisplayName("findOrCreateBySocial_save 시 DB UNIQUE 충돌_재조회 후 email 다른 OAuth user 가 점유_AUTH_EMAIL_ALREADY_LINKED_TO_DIFFERENT_PROVIDER")
    void findOrCreateBySocial_race_email충돌() {
        User other = User.createSocialUser(SocialProvider.KAKAO, "k-other", EMAIL, "n", null);
        when(userRepository.findBySocial(SocialProvider.GOOGLE, "g-1"))
                .thenReturn(Optional.empty())   // 첫 호출 — 신규
                .thenReturn(Optional.empty());  // race 후 재조회 — race winner 도 다른 사용자
        // save 직전엔 비어있었지만 race winner 가 같은 email 로 먼저 가입함
        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.empty())   // 첫 사전 체크
                .thenReturn(Optional.of(other)); // race 후 재조회
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("UNIQUE 위반"));

        assertThatThrownBy(() ->
                service.findOrCreateBySocial(SocialProvider.GOOGLE, "g-1", EMAIL, "n", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_EMAIL_ALREADY_LINKED_TO_DIFFERENT_PROVIDER);
    }

    @Test
    @DisplayName("creditPoint_affected=1_정상")
    void creditPoint_정상() {
        when(userRepository.creditPointBalance(7L, 50_000L)).thenReturn(1);

        service.creditPoint(7L, 50_000L);

        verify(userRepository, times(1)).creditPointBalance(7L, 50_000L);
    }

    @Test
    @DisplayName("creditPoint_affected=0 (미존재 userId)_USER_NOT_FOUND_트랜잭션 롤백")
    void creditPoint_미존재() {
        when(userRepository.creditPointBalance(999L, 50_000L)).thenReturn(0);

        assertThatThrownBy(() -> service.creditPoint(999L, 50_000L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("deductPoint_affected=1_정상")
    void deductPoint_정상() {
        when(userRepository.deductPointBalance(7L, 30_000L)).thenReturn(1);

        service.deductPoint(7L, 30_000L);

        verify(userRepository, times(1)).deductPointBalance(7L, 30_000L);
    }

    @Test
    @DisplayName("deductPoint_affected=0 (잔액 부족 또는 미존재)_INSUFFICIENT_POINT")
    void deductPoint_잔액부족() {
        when(userRepository.deductPointBalance(7L, 30_000L)).thenReturn(0);

        assertThatThrownBy(() -> service.deductPoint(7L, 30_000L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_POINT);
    }

    @Test
    @DisplayName("deductPoint_amount<=0_IllegalArgumentException_repository 호출 X")
    void deductPoint_invalid_amount() {
        assertThatThrownBy(() -> service.deductPoint(7L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.deductPoint(7L, -1L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).deductPointBalance(any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("creditPoint_amount<=0_IllegalArgumentException_repository 호출 X")
    void creditPoint_invalid_amount() {
        assertThatThrownBy(() -> service.creditPoint(7L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.creditPoint(7L, -1L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).creditPointBalance(any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("findOrCreateBySocial_UNIQUE 외 다른 제약 위반_원본 예외 그대로 throw")
    void findOrCreateBySocial_다른제약위반_원본throw() {
        when(userRepository.findBySocial(SocialProvider.KAKAO, "k-1")).thenReturn(Optional.empty());
        // 사전 findByEmail 도 비어있음 + race 시점 재조회도 둘 다 비어있음 → 진짜 다른 제약 위반
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        DataIntegrityViolationException nicknameTooLong =
                new DataIntegrityViolationException("nickname too long");
        when(userRepository.save(any(User.class))).thenThrow(nicknameTooLong);

        // USER_EMAIL_DUPLICATED 로 오분류되지 않고 원본 예외 그대로 노출되어야 한다
        assertThatThrownBy(() ->
                service.findOrCreateBySocial(SocialProvider.KAKAO, "k-1", EMAIL, "n", null))
                .isSameAs(nicknameTooLong);
    }

    // ───────── 관리자 통계 ─────────

    @Test
    @DisplayName("adminGetStats_total/blocked/deleted/active 모두 직접 집계")
    void adminGetStats_정상() {
        when(userRepository.countAll()).thenReturn(100L);
        when(userRepository.countBlocked()).thenReturn(5L);
        when(userRepository.countDeleted()).thenReturn(2L);
        when(userRepository.countActive()).thenReturn(93L);

        var stats = service.adminGetStats();
        assertThat(stats.total()).isEqualTo(100L);
        assertThat(stats.blocked()).isEqualTo(5L);
        assertThat(stats.deleted()).isEqualTo(2L);
        assertThat(stats.active()).isEqualTo(93L);
    }

    @Test
    @DisplayName("adminGetStats_blocked && deleted 동시 사용자_active 별도 집계라 정확 (이중 차감 회귀 방지)")
    void adminGetStats_이중_차감_없음() {
        // 시나리오: 10명 중 blocked=7, deleted=7, blocked&&deleted=5. 정확한 active = 10 - (7 + 7 - 5) = 1
        when(userRepository.countAll()).thenReturn(10L);
        when(userRepository.countBlocked()).thenReturn(7L);
        when(userRepository.countDeleted()).thenReturn(7L);
        when(userRepository.countActive()).thenReturn(1L);

        var stats = service.adminGetStats();
        assertThat(stats.active())
                .as("countActive 별도 쿼리라 이중 차감 없음 — 이전 (total - blocked - deleted) 방식이면 -4 였을 케이스")
                .isEqualTo(1L);
    }

    // ───────── 라운드 12 PR-F #8 — 활동 정지 누적 200일 자동 탈퇴 ─────────

    @org.junit.jupiter.api.Nested
    @DisplayName("adminSuspend / processAutoWithdrawal — 200일 누적 자동 탈퇴")
    class AutoWithdraw {

        private InMemoryFakeUserRepository userRepo;
        private CapturingEmailSender mailFake;
        private com.sseulang.domain.auth.application.NoOpRefreshTokenStore rtStore;
        private UserApplicationService svc;

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
            userRepo = new InMemoryFakeUserRepository();
            mailFake = new CapturingEmailSender();
            rtStore = new com.sseulang.domain.auth.application.NoOpRefreshTokenStore();
            svc = new UserApplicationService(
                    userRepo,
                    new com.sseulang.domain.transaction.application.InMemoryFakeTransactionRepository(),
                    new com.sseulang.domain.report.application.InMemoryFakeUserReportRepository(),
                    rtStore,
                    mailFake,
                    java.time.Clock.systemDefaultZone()
            );
        }

        @Test
        @DisplayName("adminSuspend_누적 200 미만은 자동 탈퇴 X")
        void adminSuspend_미달() {
            User u = userRepo.save(User.createSocialUser(SocialProvider.KAKAO, "k-1", EMAIL, "n", null));
            svc.adminSuspend(u.getId(), 50);
            svc.adminSuspend(u.getId(), 100);

            assertThat(u.getCumulativeSuspendDays()).isEqualTo(150);
            assertThat(u.isDeleted()).isFalse();
            assertThat(mailFake.toCount()).isZero();
        }

        @Test
        @DisplayName("adminSuspend_누적 200 도달_markAutoWithdrawn + 안내 메일 발송")
        void adminSuspend_도달_자동탈퇴() {
            User u = userRepo.save(User.createSocialUser(SocialProvider.KAKAO, "k-2", EMAIL, "n", null));
            svc.adminSuspend(u.getId(), 199);
            assertThat(u.isDeleted()).as("199일은 아직 미만").isFalse();
            assertThat(mailFake.toCount()).isZero();

            svc.adminSuspend(u.getId(), 1);  // 누적 200

            assertThat(u.isDeleted()).as("정확히 200 도달 시 자동 탈퇴").isTrue();
            assertThat(u.getCumulativeSuspendDays()).isEqualTo(200);
            assertThat(mailFake.toCount()).isOne();
            assertThat(mailFake.lastTo).isEqualTo(EMAIL.value());
            assertThat(mailFake.lastDays).isEqualTo(200);
        }

        @Test
        @DisplayName("processAutoWithdrawal_이미 deleted 또는 미달 user 는 no-op")
        void processAutoWithdrawal_noop() {
            User u = userRepo.save(User.createSocialUser(SocialProvider.KAKAO, "k-3", EMAIL, "n", null));

            assertThat(svc.processAutoWithdrawal(u.getId())).as("미달").isFalse();
            assertThat(mailFake.toCount()).isZero();

            u.suspend(300, java.time.LocalDateTime.now());
            u.markAutoWithdrawn();
            assertThat(svc.processAutoWithdrawal(u.getId())).as("이미 deleted").isFalse();
            assertThat(mailFake.toCount()).isZero();
        }

        @Test
        @DisplayName("findAutoWithdrawTargetIds_200 이상 + 살아있는 user 만 반환")
        void findTargets_filter() {
            User a = userRepo.save(User.createSocialUser(SocialProvider.KAKAO, "k-a", new Email("a@x.com"), "n", null));
            User b = userRepo.save(User.createSocialUser(SocialProvider.KAKAO, "k-b", new Email("b@x.com"), "n", null));
            User c = userRepo.save(User.createSocialUser(SocialProvider.KAKAO, "k-c", new Email("c@x.com"), "n", null));
            User d = userRepo.save(User.createSocialUser(SocialProvider.KAKAO, "k-d", new Email("d@x.com"), "n", null));

            a.suspend(199, java.time.LocalDateTime.now());  // 미달
            b.suspend(200, java.time.LocalDateTime.now());  // 대상
            c.suspend(500, java.time.LocalDateTime.now());  // 대상
            d.suspend(300, java.time.LocalDateTime.now());
            d.markAutoWithdrawn();                            // 이미 처리됨

            assertThat(svc.findAutoWithdrawTargetIds(100))
                    .containsExactlyInAnyOrder(b.getId(), c.getId());
        }
    }

    /** EmailSender 호출 capture — 발송 검증용 fake. */
    private static class CapturingEmailSender implements com.sseulang.domain.auth.domain.EmailSender {
        private final java.util.List<String> tos = new java.util.ArrayList<>();
        String lastTo;
        int lastDays;
        @Override public void sendVerificationEmail(String to, String url) { }
        @Override public void sendInquiryReplyEmail(String to, String s, String h) { }
        @Override public void sendAutoWithdrawnEmail(String to, int days) {
            tos.add(to); lastTo = to; lastDays = days;
        }
        int toCount() { return tos.size(); }
    }
}
