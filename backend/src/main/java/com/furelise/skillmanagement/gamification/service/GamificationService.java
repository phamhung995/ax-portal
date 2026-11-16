package com.furelise.skillmanagement.gamification.service;

import com.furelise.skillmanagement.gamification.dto.PointsHistoryItem;
import com.furelise.skillmanagement.gamification.model.Perk;
import com.furelise.skillmanagement.gamification.model.PerkRedemption;

import java.util.List;

/**
 * Gamification: the points currency (guide §6). Points and reputation are
 * separate currencies — points buy utilities/status, never trust privileges.
 * Earn values (per action): CHECK_IN +10, PUBLISH_SKILL +50, INSTALL +5,
 * RATING +2, COMMENT +2, UPVOTE +1, READ_NEWS +1, fulfill a request +80.
 * Daily caps stop farming: RATING/COMMENT/UPVOTE 10, READ_NEWS 5, INSTALL 25.
 */
public interface GamificationService {

    /** Earn values per action (single source of truth for the UI too). */
    int POINTS_CHECK_IN = 10;
    int POINTS_PUBLISH_SKILL = 50;
    int POINTS_INSTALL = 5;
    int POINTS_RATING = 2;
    int POINTS_COMMENT = 2;
    int POINTS_UPVOTE = 1;
    int POINTS_READ_NEWS = 1;
    int POINTS_FULFILL_REQUEST = 80;
    int POINTS_STREAK_BONUS_7D = 20;

    /** Current balance (0 when the user has never earned). */
    int balance(Long userId);

    /**
     * Award points for an action. Returns the points actually granted (0 when
     * the daily cap for the type is already reached, or the reward was already
     * granted once for this object — e.g. publish +50 is once per skill).
     */
    int awardPoints(Long userId, int amount, String type, String reason, String refType, Long refId);

    /** Spend points (perks, bounty escrow). Throws when the balance is short. */
    void spendPoints(Long userId, int amount, String type, String reason, String refType, Long refId);

    /** Daily check-in: +10, streak bonus +20 every 7th consecutive day. */
    CheckInResult checkIn(Long userId);

    /** Points wallet snapshot for the header / Home widget. */
    MeResponse me(Long userId);

    /** Active perks in the points store, cheapest first. */
    List<Perk> perks();

    /** Buy a perk with points. */
    PerkRedemption redeemPerk(Long userId, Long perkId);

    /** Ledger history, newest first. */
    List<PointsHistoryItem> history(Long userId, int page, int pageSize);

    record CheckInResult(int balance, int streak, int awardedPoints, int bonusPoints, boolean alreadyCheckedIn) {
    }

    record MeResponse(int balance, boolean checkedInToday, int streak, int activeRedemptions) {
    }
}
