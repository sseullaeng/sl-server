package com.sseulang.domain.user.application;

import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.user.domain.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryFakeUserRepository implements UserRepository {

    private final Map<Long, User> store = new HashMap<>();
    private long sequence = 0;
    private final AtomicInteger recomputeCalls = new AtomicInteger();

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<User> findBySocial(SocialProvider provider, String socialId) {
        return store.values().stream()
                .filter(u -> u.getSocialProvider() == provider && socialId.equals(u.getSocialId()))
                .findFirst();
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return store.values().stream()
                .filter(u -> email.value().equals(u.getEmail()))
                .findFirst();
    }

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            ReflectionTestUtils.setField(user, "id", ++sequence);
        }
        store.put(user.getId(), user);
        return user;
    }

    @Override
    public int recordReviewFor(Long revieweeId, int rating) {
        recomputeCalls.incrementAndGet();
        // fake — 실제 누적은 단위 테스트에서 검증 안 함 (호출 카운트만). 통합 IT 에서 prod SQL 실행 검증.
        return store.containsKey(revieweeId) ? 1 : 0;
    }

    public int recomputeCallCount() {
        return recomputeCalls.get();
    }

    @Override
    public int creditPointBalance(Long userId, long amount) {
        User user = store.get(userId);
        if (user == null) return 0;
        ReflectionTestUtils.setField(user, "pointBalance", user.getPointBalance() + amount);
        return 1;
    }

    @Override
    public int deductPointBalance(Long userId, long amount) {
        User user = store.get(userId);
        if (user == null) return 0;
        if (user.getPointBalance() < amount) return 0;  // 잔액 부족 시 affected=0
        ReflectionTestUtils.setField(user, "pointBalance", user.getPointBalance() - amount);
        return 1;
    }

    @Override
    public Long findPointBalance(Long userId) {
        User user = store.get(userId);
        return user == null ? null : user.getPointBalance();
    }

    @Override
    public Page<User> findAllForAdmin(Pageable pageable) {
        List<User> all = store.values().stream()
                .sorted(Comparator.comparing(User::getId).reversed())
                .toList();
        return new PageImpl<>(all, pageable, all.size());
    }
}
