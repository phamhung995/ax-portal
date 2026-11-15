package com.furelise.skillmanagement.auth.service;

import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;

/**
 * Shared password policy (Login SRS §14 / Settings SRS):
 * min 8 chars, at least one uppercase, one lowercase, one digit
 * and one special character.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new BusinessException(BusinessErrorCode.PASSWORD_TOO_SHORT, "Password cannot be empty.");
        }
        if (password.length() < 8) {
            throw new BusinessException(BusinessErrorCode.PASSWORD_TOO_SHORT,
                    "Password must be at least 8 characters.");
        }
        boolean upper = password.chars().anyMatch(Character::isUpperCase);
        boolean lower = password.chars().anyMatch(Character::isLowerCase);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        boolean special = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        if (!upper || !lower || !digit || !special) {
            throw new BusinessException(BusinessErrorCode.PASSWORD_WEAK,
                    "Password must contain uppercase, lowercase, a number and a special character.");
        }
    }

    /** 0..4 = number of satisfied rules (special char counts as 4th). */
    public static int strengthScore(String password) {
        if (password == null || password.isEmpty()) {
            return 0;
        }
        int score = 0;
        if (password.length() >= 8) score++;
        if (password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)) score++;
        if (password.chars().anyMatch(Character::isDigit)) score++;
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) score++;
        return score;
    }
}
