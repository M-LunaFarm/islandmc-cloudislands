package kr.lunaf.cloudislands.paper.command;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import kr.lunaf.cloudislands.protocol.route.RouteFailureMessagePolicy;

final class IslandCommandMessages {
    private static final Map<String, String> CODE_MESSAGE_KEYS = Map.ofEntries(
        Map.entry("OWNER_ROLE_PROTECTED", "failure-code-owner-role-protected"),
        Map.entry("MEMBER_ROLE_UNAVAILABLE", "failure-code-member-role-unavailable"),
        Map.entry("VISITOR_BAN_DENIED", "failure-code-visitor-ban-denied"),
        Map.entry("REVIEW_OWNER_DENIED", "failure-code-review-owner-denied"),
        Map.entry("REVIEW_RATING_INVALID", "failure-code-review-rating-invalid"),
        Map.entry("INSUFFICIENT_ITEMS", "failure-code-insufficient-items"),
        Map.entry("ECONOMY_CHARGE_FAILED", "failure-code-economy-charge-failed"),
        Map.entry("ECONOMY_REFUND_FAILED", "failure-code-economy-refund-failed"),
        Map.entry("CORE_CREATE_FAILED_REFUNDED", "failure-code-core-create-failed-refunded"),
        Map.entry("CREATE_IN_PROGRESS", "failure-code-create-in-progress"),
        Map.entry("ROUTE_TICKET_UNAVAILABLE", "failure-code-route-ticket-unavailable"),
        Map.entry("FAILED_CREATE_TEMPLATE_MISMATCH", "failure-code-failed-create-template-mismatch"),
        Map.entry("TEMPLATE_PERMISSION_DENIED", "failure-code-template-permission-denied")
    );

    private IslandCommandMessages() {
    }

    static String playerCodeMessage(String code, String fallback) {
        return playerCodeMessage(code, fallback, (_key, keyFallback) -> keyFallback);
    }

    static String playerCodeMessage(String code, String fallback, MessageLookup messages) {
        if (code == null || code.isBlank()) {
            return fallback;
        }
        MessageLookup lookup = messages == null ? (_key, keyFallback) -> keyFallback : messages;
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        String message = codeMessage(normalizedCode, fallback, lookup);
        String hint = codeHint(normalizedCode, lookup);
        return hint.isBlank() ? message : message + hint;
    }

    private static String codeMessage(String code, String fallback, MessageLookup messages) {
        String key = CODE_MESSAGE_KEYS.get(code);
        if (key != null) {
            return messages.message(key, fallback);
        }
        String policyMessage = RouteFailureMessagePolicy.playerMessage(code, fallback);
        if (!Objects.equals(policyMessage, fallback)
            || !RouteFailureMessagePolicy.FALLBACK_CATEGORY.equals(RouteFailureMessagePolicy.playerSafeCategory(code))) {
            return messages.message("failure-code-" + codeKey(code), policyMessage);
        }
        return fallback;
    }

    private static String codeHint(String code, MessageLookup messages) {
        String specificHint = messages.message("failure-code-" + codeKey(code) + "-hint", "");
        if (!specificHint.isBlank()) {
            return specificHint;
        }
        return switch (RouteFailureMessagePolicy.playerSafeCategory(code)) {
            case RouteFailureMessagePolicy.CAPACITY_CATEGORY -> messages.message("failure-code-capacity-hint", "");
            case RouteFailureMessagePolicy.MAINTENANCE_CATEGORY -> messages.message("failure-code-maintenance-hint", "");
            case RouteFailureMessagePolicy.PERMISSION_CATEGORY -> messages.message("failure-code-permission-hint", "");
            case RouteFailureMessagePolicy.RATE_LIMIT_CATEGORY -> messages.message("failure-code-rate-limit-hint", "");
            case RouteFailureMessagePolicy.TRANSIENT_CATEGORY -> messages.message("failure-code-transient-hint", "");
            default -> "";
        };
    }

    private static String codeKey(String code) {
        return code.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    @FunctionalInterface
    interface MessageLookup {
        String message(String key, String fallback);
    }
}
