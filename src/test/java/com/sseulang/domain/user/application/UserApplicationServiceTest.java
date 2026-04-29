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
    @DisplayName("findOrCreateBySocial_신규인데 email 이미 다른 provider 로 가입됨_USER_EMAIL_DUPLICATED")
    void findOrCreateBySocial_email중복() {
        User other = User.createSocialUser(SocialProvider.KAKAO, "k-other", EMAIL, "n", null);
        when(userRepository.findBySocial(SocialProvider.GOOGLE, "g-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(other));

        assertThatThrownBy(() ->
                service.findOrCreateBySocial(SocialProvider.GOOGLE, "g-1", EMAIL, "n", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_EMAIL_DUPLICATED);

        verify(userRepository, never()).save(any());
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
    @DisplayName("findOrCreateBySocial_save 시 DB UNIQUE 충돌_재조회 후 email 다른 user 가 점유_USER_EMAIL_DUPLICATED")
    void findOrCreateBySocial_race_email충돌() {
        User other = User.createSocialUser(SocialProvider.KAKAO, "k-other", EMAIL, "n", null);
        when(userRepository.findBySocial(SocialProvider.KAKAO, "k-1"))
                .thenReturn(Optional.empty())   // 첫 호출 — 신규
                .thenReturn(Optional.empty());  // race 후 재조회 — race winner 도 다른 사용자
        // save 직전엔 비어있었지만 race winner 가 같은 email 로 먼저 가입함
        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.empty())   // 첫 사전 체크
                .thenReturn(Optional.of(other)); // race 후 재조회
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("UNIQUE 위반"));

        assertThatThrownBy(() ->
                service.findOrCreateBySocial(SocialProvider.KAKAO, "k-1", EMAIL, "n", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_EMAIL_DUPLICATED);
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
}
