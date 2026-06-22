package kr.lunaf.cloudislands.paper.command;

import java.util.Objects;
import kr.lunaf.cloudislands.protocol.route.RouteFailureMessagePolicy;

final class IslandCommandMessages {
    private IslandCommandMessages() {
    }

    static String playerCodeMessage(String code, String fallback) {
        if (code == null || code.isBlank()) {
            return fallback;
        }
        String policyMessage = RouteFailureMessagePolicy.playerMessage(code, fallback);
        if (!Objects.equals(policyMessage, fallback)
            || !RouteFailureMessagePolicy.FALLBACK_CATEGORY.equals(RouteFailureMessagePolicy.playerSafeCategory(code))) {
            return policyMessage;
        }
        return switch (code) {
            case "OWNER_ROLE_PROTECTED" -> "섬 소유자는 소유권 양도로만 변경할 수 있습니다.";
            case "MEMBER_ROLE_UNAVAILABLE" -> "멤버 역할로 사용할 수 없는 값입니다.";
            case "VISITOR_BAN_DENIED" -> "섬 멤버는 방문자 밴으로 처리할 수 없습니다.";
            case "REVIEW_OWNER_DENIED" -> "자기 섬은 평가할 수 없습니다.";
            case "REVIEW_RATING_INVALID" -> "평점은 1~5 사이여야 합니다.";
            case "INSUFFICIENT_ITEMS" -> "섬 창고 수량이 부족합니다.";
            default -> fallback;
        };
    }
}
