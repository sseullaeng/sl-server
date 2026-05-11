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
    public synchronized int holdForEscrow(Long userId, long amount) {
        User user = store.get(userId);
        if (user == null) return 0;
        if (user.getPointBalance() < amount) return 0;
        ReflectionTestUtils.setField(user, "pointBalance", user.getPointBalance() - amount);
        ReflectionTestUtils.setField(user, "pointHold", user.getPointHold() + amount);
        return 1;
    }

    @Override
    public synchronized int releaseHold(Long userId, long amount) {
        User user = store.get(userId);
        if (user == null) return 0;
        if (user.getPointHold() < amount) return 0;
        ReflectionTestUtils.setField(user, "pointHold", user.getPointHold() - amount);
        return 1;
    }

    @Override
    public synchronized int refundHold(Long userId, long amount) {
        User user = store.get(userId);
        if (user == null) return 0;
        if (user.getPointHold() < amount) return 0;
        ReflectionTestUtils.setField(user, "pointHold", user.getPointHold() - amount);
        ReflectionTestUtils.setField(user, "pointBalance", user.getPointBalance() + amount);
        return 1;
    }

    @Override
    public Long findPointHold(Long userId) {
        User user = store.get(userId);
        return user == null ? null : user.getPointHold();
    }

    @Override
    public synchronized java.util.Optional<PointSnapshot> findPointSnapshot(Long userId) {
        User user = store.get(userId);
        if (user == null) return java.util.Optional.empty();
        return java.util.Optional.of(new PointSnapshot(user.getPointBalance(), user.getPointHold()));
    }

    @Override
    public Page<User> findAllForAdmin(Pageable pageable) {
        List<User> all = store.values().stream()
                .sorted(Comparator.comparing(User::getId).reversed())
                .toList();
        return new PageImpl<>(all, pageable, all.size());
    }

    @Override
    public long countAll() {
        return store.size();
    }

    @Override
    public long countBlocked() {
        return store.values().stream().filter(User::isBlocked).count();
    }

    @Override
    public long countDeleted() {
        return store.values().stream().filter(User::isDeleted).count();
    }

    @Override
    public long countActive() {
        return store.values().stream().filter(u -> !u.isBlocked() && !u.isDeleted()).count();
    }

    @Override
    public long countSignupsBetween(java.time.LocalDateTime from, java.time.LocalDateTime to) {
        return store.values().stream()
                .filter(u -> {
                    java.time.LocalDateTime c = u.getCreatedAt();
                    return c != null && !c.isBefore(from) && c.isBefore(to);
                })
                .count();
    }

    @Override
    public java.util.List<DailyCount> findDailySignups(java.time.LocalDateTime from, java.time.LocalDateTime to) {
        java.util.Map<java.time.LocalDate, Long> grouped = new java.util.TreeMap<>();
        for (User u : store.values()) {
            java.time.LocalDateTime c = u.getCreatedAt();
            if (c == null || c.isBefore(from) || !c.isBefore(to)) continue;
            grouped.merge(c.toLocalDate(), 1L, Long::sum);
        }
        return grouped.entrySet().stream()
                .map(e -> new DailyCount(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    public Page<User> searchForAdmin(
            com.sseulang.domain.user.application.dto.AdminUserSearchCriteria criteria,
            java.time.LocalDateTime now,
            int dormantThresholdDays,
            Pageable pageable
    ) {
        // 테스트 fake — 검색·필터 미구현. 기존 findAllForAdmin 으로 대체 위임.
        return findAllForAdmin(pageable);
    }

    @Override
    public java.util.List<Long> findActiveIdsAfter(long afterId, int limit) {
        return store.values().stream()
                .filter(u -> !u.isBlocked() && !u.isDeleted())
                .map(User::getId)
                .filter(id -> id > afterId)
                .sorted()
                .limit(limit)
                .toList();
    }

    @Override
    public java.util.List<Long> findIdsByKeywordLike(String keyword, int limit) {
        if (keyword == null || keyword.isBlank() || limit <= 0) return java.util.Collections.emptyList();
        String kw = keyword.strip().toLowerCase();
        return store.values().stream()
                .filter(u -> {
                    String email = u.getEmail() == null ? "" : u.getEmail().toLowerCase();
                    String nick  = u.getNickname() == null ? "" : u.getNickname().toLowerCase();
                    return email.contains(kw) || nick.contains(kw);
                })
                .map(User::getId)
                .sorted()
                .limit(limit)
                .toList();
    }

    @Override
    public java.util.List<Long> findAutoWithdrawTargetIds(int threshold, int limit) {
        if (limit <= 0) return java.util.Collections.emptyList();
        return store.values().stream()
                .filter(u -> !u.isDeleted() && u.getCumulativeSuspendDays() >= threshold)
                .map(User::getId)
                .sorted()
                .limit(limit)
                .toList();
    }
}
