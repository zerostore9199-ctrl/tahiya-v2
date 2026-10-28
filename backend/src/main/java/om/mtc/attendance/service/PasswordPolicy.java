package om.mtc.attendance.service;

import om.mtc.attendance.web.error.ApiException;

/** Minimum password rules: 10+ characters with at least one letter and one digit. */
public final class PasswordPolicy {
    private PasswordPolicy() {
    }

    public static void check(String password, String username) {
        if (password == null || password.length() < 10 || password.length() > 128) {
            throw ApiException.badRequest("WEAK_PASSWORD", "كلمة المرور يجب أن تكون بين 10 و128 حرفاً");
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            throw ApiException.badRequest("WEAK_PASSWORD", "كلمة المرور يجب أن تحتوي على حروف وأرقام");
        }
        if (username != null && password.toLowerCase().contains(username.toLowerCase())) {
            throw ApiException.badRequest("WEAK_PASSWORD", "كلمة المرور يجب ألا تحتوي على اسم المستخدم");
        }
    }
}
