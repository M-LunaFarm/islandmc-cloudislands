package kr.lunaf.cloudislands.protocol.route;

public final class RouteFailureMessagePolicy {
    public static final String CAPACITY_MESSAGE = "현재 섬 서비스가 혼잡합니다. 잠시 후 다시 시도해주세요.";
    public static final String MAINTENANCE_MESSAGE = "현재 섬 서비스 일부 기능이 점검 중입니다.";
    public static final String CAPACITY_CATEGORY = "capacity";
    public static final String DOMAIN_CATEGORY = "domain";
    public static final String FALLBACK_CATEGORY = "fallback";
    public static final String MAINTENANCE_CATEGORY = "maintenance";
    public static final String PERMISSION_CATEGORY = "permission";
    public static final String RATE_LIMIT_CATEGORY = "rate-limit";
    public static final String TRANSIENT_CATEGORY = "transient";

    private RouteFailureMessagePolicy() {
    }

    public static String playerMessage(String code, String fallback) {
        if (code == null || code.isBlank()) {
            return fallback;
        }
        if (capacityCode(code)) {
            return CAPACITY_MESSAGE;
        }
        if (maintenanceCode(code)) {
            return MAINTENANCE_MESSAGE;
        }
        return switch (code) {
            case "ALREADY_HAS_ISLAND" -> "이미 섬을 보유하고 있습니다.";
            case "TEMPLATE_UNAVAILABLE" -> "사용할 수 없는 섬 템플릿입니다.";
            case "PLAYER_NOT_FOUND" -> "플레이어를 찾을 수 없습니다.";
            case "ISLAND_NOT_FOUND" -> "섬을 찾을 수 없습니다.";
            case "ISLAND_PRIVATE" -> "해당 섬은 비공개 상태입니다.";
            case "ISLAND_LOCKED" -> "해당 섬은 현재 잠겨 있습니다.";
            case "VISITOR_BANNED" -> "해당 섬에 방문할 수 없습니다.";
            case "VISITOR_SOFT_FULL" -> "해당 섬은 지금 멤버 입장 슬롯을 우선 사용 중입니다. 잠시 후 다시 시도해주세요.";
            case "ACTIVATION_LOCKED" -> "섬을 준비하는 중입니다. 잠시 후 다시 시도해주세요.";
            case "NODE_UNAVAILABLE" -> CAPACITY_MESSAGE;
            case "STORAGE_UNAVAILABLE" -> "현재 섬 저장소를 준비하는 중입니다. 잠시 후 다시 시도해주세요.";
            case "TARGET_OFFLINE_NO_ISLAND" -> "대상 플레이어의 섬을 찾을 수 없습니다.";
            case "PUBLIC_ISLAND_NOT_FOUND" -> "방문 가능한 공개 섬을 찾지 못했습니다.";
            case "WARP_NOT_FOUND" -> "해당 워프를 찾을 수 없습니다.";
            case "WARP_PRIVATE" -> "해당 워프는 공개 상태가 아닙니다.";
            case "WARP_LIMIT" -> "섬 워프 한도에 도달했습니다.";
            case "ISLAND_MIGRATING" -> "섬 서버를 최적화하는 중입니다. 잠시 후 자동으로 이동됩니다.";
            case "ISLAND_LOADING_FAILED" -> "섬을 준비하지 못했습니다. 잠시 후 다시 시도해주세요.";
            case "JOB_QUEUE_UNAVAILABLE", "RECOVERY_UNAVAILABLE" -> MAINTENANCE_MESSAGE;
            case "ROUTE_TICKET_NOT_FOUND", "ROUTE_ROUTE_NOT_FOUND" -> "섬 이동 세션이 만료되었습니다. 다시 시도해주세요.";
            case "ISLAND_PERMISSION_DENIED" -> "섬 권한이 없습니다.";
            case "MEMBER_LIMIT" -> "섬 멤버 한도에 도달했습니다.";
            case "BANK_LIMIT" -> "섬 은행 한도에 도달했습니다.";
            case "INVALID_AMOUNT" -> "올바른 금액을 입력해주세요.";
            case "INSUFFICIENT_FUNDS" -> "잔액이 부족합니다.";
            case "UNKNOWN_UPGRADE" -> "알 수 없는 업그레이드입니다.";
            case "MAX_LEVEL" -> "이미 최대 업그레이드 레벨입니다.";
            case "INVITE_UNAVAILABLE" -> "사용할 수 없는 초대입니다.";
            case "OWNERSHIP_TRANSFER_DENIED" -> "섬 소유권을 양도할 수 없습니다.";
            case "UNAUTHORIZED", "ADMIN_PERMISSION_DENIED" -> "이 명령을 사용할 권한이 없습니다.";
            case "RATE_LIMITED" -> "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";
            default -> fallback;
        };
    }

    public static String playerSafeCategory(String code) {
        if (code == null || code.isBlank()) {
            return FALLBACK_CATEGORY;
        }
        if (capacityCode(code)) {
            return CAPACITY_CATEGORY;
        }
        if (maintenanceCode(code)) {
            return MAINTENANCE_CATEGORY;
        }
        return switch (code) {
            case "ALREADY_HAS_ISLAND",
                "TEMPLATE_UNAVAILABLE",
                "PLAYER_NOT_FOUND",
                "ISLAND_NOT_FOUND",
                "ISLAND_PRIVATE",
                "ISLAND_LOCKED",
                "VISITOR_BANNED",
                "VISITOR_SOFT_FULL",
                "TARGET_OFFLINE_NO_ISLAND",
                "PUBLIC_ISLAND_NOT_FOUND",
                "WARP_NOT_FOUND",
                "WARP_PRIVATE",
                "WARP_LIMIT",
                "ISLAND_PERMISSION_DENIED",
                "MEMBER_LIMIT",
                "BANK_LIMIT",
                "INVALID_AMOUNT",
                "INSUFFICIENT_FUNDS",
                "UNKNOWN_UPGRADE",
                "MAX_LEVEL",
                "INVITE_UNAVAILABLE",
                "OWNERSHIP_TRANSFER_DENIED" -> DOMAIN_CATEGORY;
            case "ACTIVATION_LOCKED",
                "ISLAND_MIGRATING",
                "ISLAND_LOADING_FAILED",
                "ROUTE_TICKET_NOT_FOUND",
                "ROUTE_ROUTE_NOT_FOUND" -> TRANSIENT_CATEGORY;
            case "UNAUTHORIZED", "ADMIN_PERMISSION_DENIED" -> PERMISSION_CATEGORY;
            case "RATE_LIMITED" -> RATE_LIMIT_CATEGORY;
            default -> FALLBACK_CATEGORY;
        };
    }

    public static boolean capacityCode(String code) {
        return code != null
            && (code.startsWith("NO_READY_NODE")
            || code.startsWith("TARGET_NODE")
            || code.startsWith("ACTIVE_NODE")
            || code.startsWith("NO_CAPACITY")
            || code.startsWith("FULL_NODE")
            || code.startsWith("ROUTE_ALLOCATOR")
            || code.equals("POOL_EMPTY")
            || code.equals("POOL_MISMATCH")
            || code.equals("MAX_ACTIVATION_QUEUE")
            || code.equals("MAX_ACTIVE_ISLANDS")
            || code.equals("HARD_PLAYER_CAP")
            || code.equals("STATE_SOFT_FULL")
            || code.equals("STATE_HARD_FULL"));
    }

    public static boolean maintenanceCode(String code) {
        return code != null
            && (code.startsWith("HEARTBEAT_")
            || code.startsWith("STORAGE_")
            || code.startsWith("OBJECT_STORAGE_")
            || code.startsWith("TEMPLATE_VERSION_")
            || code.startsWith("UNSUPPORTED_TEMPLATE")
            || code.equals("TEMPLATE_UNSUPPORTED")
            || code.equals("NODE_VERSION_TOO_OLD")
            || code.equals("DEFAULT_NODE_IDENTITY")
            || code.startsWith("TARGET_SERVER_")
            || code.startsWith("ROUTE_READY_")
            || code.startsWith("ROUTE_STATUS_")
            || code.startsWith("SESSION_PUBLISH_")
            || code.startsWith("CONNECT_")
            || code.startsWith("PENDING_"));
    }
}
