package kr.lunaf.cloudislands.paper.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class IslandRoleKeyPolicy {
    private static final List<String> MEMBER_ROLE_KEYS = List.of("CO_OWNER", "MODERATOR", "MEMBER", "TRUSTED");
    private static final Set<String> RESERVED_ROLE_KEYS = Set.of("OWNER", "VISITOR", "BANNED");
    private static final Map<String, Integer> DEFAULT_WEIGHTS = Map.of(
        "OWNER", 0,
        "CO_OWNER", 1,
        "MODERATOR", 2,
        "MEMBER", 3,
        "TRUSTED", 4,
        "VISITOR", 5,
        "BANNED", 6
    );

    private IslandRoleKeyPolicy() {
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    static boolean editable(String roleKey) {
        return !roleKey.isBlank()
            && roleKey.matches("[A-Z0-9_]{1,32}")
            && !RESERVED_ROLE_KEYS.contains(roleKey);
    }

    static int defaultWeight(String roleKey) {
        return DEFAULT_WEIGHTS.getOrDefault(normalize(roleKey), 100);
    }

    static List<String> memberRoleKeys() {
        return MEMBER_ROLE_KEYS;
    }

    static String visitorRoleKey() {
        return "VISITOR";
    }
}
