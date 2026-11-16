package com.furelise.skillmanagement.gamification.service.impl;

import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.gamification.dto.PointsHistoryItem;
import com.furelise.skillmanagement.gamification.model.DailyCheckin;
import com.furelise.skillmanagement.gamification.model.DailyCheckinId;
import com.furelise.skillmanagement.gamification.model.Perk;
import com.furelise.skillmanagement.gamification.model.PerkRedemption;
import com.furelise.skillmanagement.gamification.model.PointsTransaction;
import com.furelise.skillmanagement.gamification.model.UserPoints;
import com.furelise.skillmanagement.gamification.repository.DailyCheckinRepository;
import com.furelise.skillmanagement.gamification.repository.PerkRedemptionRepository;
import com.furelise.skillmanagement.gamification.repository.PerkRepository;
import com.furelise.skillmanagement.gamification.repository.PointsTransactionRepository;
import com.furelise.skillmanagement.gamification.repository.UserPointsRepository;
import com.furelise.skillmanagement.gamification.service.GamificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Points ledger. Every movement is one row in points_transactions; the
 * balance in user_points is a cache updated in the same transaction.
 * The portal is used in Vietnam, so "today" is Asia/Ho_Chi_Minh.
 */
@Service
@RequiredArgsConstructor
public class GamificationServiceImpl implements GamificationService {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Daily earning caps per action type (anti-farming "valves"). */
    private static final Map<String, Integer> DAILY_CAPS = Map.of(
            "RATING", 10,
            "COMMENT", 10,
            "UPVOTE", 10,
            "READ_NEWS", 5,
            "INSTALL", 25);

    /** Types rewarded at most once per referenced object (not per day). */
    private static final List<String> ONCE_PER_REF = List.of("PUBLISH_SKILL");

    /** Expiry (days) per perk code, null = never expires. */
    private static final Map<String, Long> PERK_EXPIRY_DAYS = Map.of(
            "STREAK_FREEZE", 30L,
            "SKILL_BOOST_7D", 7L,
            "SKILL_BOOST_30D", 30L);

    private final UserPointsRepository userPointsRepository;
    private final PointsTransactionRepository transactionRepository;
    private final DailyCheckinRepository checkinRepository;
    private final PerkRepository perkRepository;
    private final PerkRedemptionRepository redemptionRepository;

    @Override
    @Transactional(readOnly = true)
    public int balance(Long userId) {
        return userPointsRepository.findById(userId).map(UserPoints::getBalance).orElse(0);
    }

    @Override
    @Transactional
    public int awardPoints(Long userId, int amount, String type, String reason,
                           String refType, Long refId) {
        if (amount <= 0) {
            return 0;
        }
        if (ONCE_PER_REF.contains(type) && refType != null && refId != null
                && transactionRepository.existsByTypeAndRefTypeAndRefId(type, refType, refId)) {
            return 0;
        }
        Integer cap = DAILY_CAPS.get(type);
        if (cap != null) {
            Instant dayStart = LocalDate.now(VN).atStartOfDay(VN).toInstant();
            int earnedToday = transactionRepository.sumAmountByUserIdAndTypeSince(userId, type, dayStart);
            if (earnedToday >= cap) {
                return 0;
            }
            amount = Math.min(amount, cap - earnedToday);
        }
        apply(userId, amount, type, reason, refType, refId);
        return amount;
    }

    @Override
    @Transactional
    public void spendPoints(Long userId, int amount, String type, String reason,
                            String refType, Long refId) {
        if (amount <= 0) {
            throw new BusinessException(BusinessErrorCode.GAMIFICATION_INVALID_AMOUNT,
                    "Amount must be positive.");
        }
        int current = balance(userId);
        if (current < amount) {
            throw new BusinessException(BusinessErrorCode.GAMIFICATION_INSUFFICIENT_POINTS,
                    "Not enough points (have " + current + ", need " + amount + ").");
        }
        apply(userId, -amount, type, reason, refType, refId);
    }

    @Override
    @Transactional
    public CheckInResult checkIn(Long userId) {
        LocalDate today = LocalDate.now(VN);
        if (checkinRepository.findByUserAndDate(userId, today).isPresent()) {
            return new CheckInResult(balance(userId), currentStreak(userId), 0, 0, true);
        }
        int streak = 1;
        LocalDate yesterday = today.minusDays(1);
        var yesterdayRow = checkinRepository.findByUserAndDate(userId, yesterday);
        if (yesterdayRow.isPresent()) {
            streak = yesterdayRow.get().getStreak() + 1;
        }
        checkinRepository.save(DailyCheckin.builder()
                .id(new DailyCheckinId(userId, today))
                .streak(streak)
                .createdAt(Instant.now())
                .build());
        int awarded = POINTS_CHECK_IN;
        apply(userId, awarded, "CHECK_IN", "Điểm danh hằng ngày", null, null);
        int bonus = 0;
        if (streak % 7 == 0) {
            bonus = POINTS_STREAK_BONUS_7D;
            apply(userId, bonus, "STREAK_BONUS", "Thưởng chuỗi " + streak + " ngày", null, null);
        }
        return new CheckInResult(balance(userId), streak, awarded, bonus, false);
    }

    @Override
    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        LocalDate today = LocalDate.now(VN);
        boolean checkedIn = checkinRepository.findByUserAndDate(userId, today).isPresent();
        return new MeResponse(balance(userId), checkedIn, currentStreak(userId),
                redemptionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                        .filter(r -> "ACTIVE".equals(r.getStatus())).toList().size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Perk> perks() {
        return perkRepository.findByActiveOrderByCostAsc("Y");
    }

    @Override
    @Transactional
    public PerkRedemption redeemPerk(Long userId, Long perkId) {
        Perk perk = perkRepository.findById(perkId).orElseThrow(() ->
                new BusinessException(BusinessErrorCode.GAMIFICATION_PERK_NOT_FOUND, "Perk not found."));
        if (!"Y".equals(perk.getActive())) {
            throw new BusinessException(BusinessErrorCode.GAMIFICATION_PERK_INACTIVE,
                    "Perk is no longer available.");
        }
        spendPoints(userId, perk.getCost(), "PERK_REDEEM", "Đổi perk: " + perk.getName(), "PERK", perkId);
        Long days = PERK_EXPIRY_DAYS.get(perk.getCode());
        return redemptionRepository.save(PerkRedemption.builder()
                .userId(userId)
                .perkId(perkId)
                .cost(perk.getCost())
                .status("ACTIVE")
                .expiresAt(days == null ? null : Instant.now().plus(days, ChronoUnit.DAYS))
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PointsHistoryItem> history(Long userId, int page, int pageSize) {
        int p = Math.max(page, 1);
        int size = Math.min(Math.max(pageSize, 1), 50);
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(p - 1, size))
                .map(t -> new PointsHistoryItem(t.getId(), t.getAmount(), t.getType(),
                        t.getReason(), t.getCreatedAt()))
                .getContent();
    }

    private int currentStreak(Long userId) {
        return checkinRepository.findFirstByIdUserIdOrderByIdCheckinDateDesc(userId)
                .map(DailyCheckin::getStreak).orElse(0);
    }

    /** One ledger row + balance cache update (same transaction). */
    private void apply(Long userId, int amount, String type, String reason, String refType, Long refId) {
        transactionRepository.save(PointsTransaction.builder()
                .userId(userId)
                .amount(amount)
                .type(type)
                .reason(reason)
                .refType(refType)
                .refId(refId)
                .build());
        UserPoints points = userPointsRepository.findById(userId)
                .orElse(UserPoints.builder().userId(userId).build());
        points.setBalance(points.getBalance() + amount);
        points.setUpdatedAt(Instant.now());
        userPointsRepository.save(points);
    }
}
