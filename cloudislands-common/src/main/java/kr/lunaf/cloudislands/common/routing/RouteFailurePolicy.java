package kr.lunaf.cloudislands.common.routing;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RouteFailurePolicy {
    public static final String PUBLIC_FAILURE_CODES = "ISLAND_NOT_FOUND,ISLAND_PRIVATE,VISITOR_BANNED,NODE_UNAVAILABLE,ISLAND_LOADING_FAILED,TARGET_OFFLINE_NO_ISLAND,PUBLIC_ISLAND_NOT_FOUND";
    public static final String PUBLIC_MESSAGE_POLICY = "player-messages-hide-node-id-shard-cell-and-debug-reason";
    public static final String DEBUG_REASON_POLICY = "raw-routing-block-reason-visible-only-in-admin-route-debug-events";
    public static final String VISIT_REJECTION_POLICY = "visit-rejections-use-public-code-and-admin-only-debug-reason";

    private static final Map<String, String> PUBLIC_MESSAGES = new LinkedHashMap<>();

    static {
        PUBLIC_MESSAGES.put("ISLAND_PRIVATE", "해당 섬은 비공개 상태입니다.");
        PUBLIC_MESSAGES.put("VISITOR_BANNED", "해당 섬에 방문할 수 없습니다.");
        PUBLIC_MESSAGES.put("NODE_UNAVAILABLE", "현재 섬 서버가 혼잡합니다. 잠시 후 다시 시도해주세요.");
        PUBLIC_MESSAGES.put("ISLAND_LOADING_FAILED", "섬을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
        PUBLIC_MESSAGES.put("TARGET_OFFLINE_NO_ISLAND", "대상 플레이어의 섬을 찾을 수 없습니다.");
    }

    private RouteFailurePolicy() {
    }

    public static String publicMessage(String code) {
        if (code == null || code.isBlank()) {
            return "섬 이동을 처리할 수 없습니다. 잠시 후 다시 시도해주세요.";
        }
        return PUBLIC_MESSAGES.getOrDefault(code, "섬 이동을 처리할 수 없습니다. 잠시 후 다시 시도해주세요.");
    }

    public static String publicMessageSummary() {
        return PUBLIC_MESSAGES.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .reduce((left, right) -> left + "|" + right)
            .orElse("");
    }

    public static boolean debugReasonPlayerVisible() {
        return false;
    }
}
