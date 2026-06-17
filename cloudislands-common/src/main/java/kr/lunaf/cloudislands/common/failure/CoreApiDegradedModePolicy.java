package kr.lunaf.cloudislands.common.failure;

import java.util.Set;

public final class CoreApiDegradedModePolicy {
    public static final String CONTRACT = "core-api-down-keeps-loaded-islands-playable-and-blocks-control-plane-writes";
    public static final String ACTIVE_ISLAND_PLAY_POLICY = "loaded active islands continue play on the current Paper node";
    public static final String LOCAL_CACHE_PROTECTION_POLICY = "local protection cache remains authoritative for already loaded islands";
    public static final String BASIC_TELEPORT_POLICY = "basic teleport may use local home fallback while Core API is unavailable";
    public static final String RESTRICTED_OPERATION_POLICY = "new island create, inactive activation, island move, member changes, and flag changes are restricted";
    public static final String MAINTENANCE_MESSAGE = "현재 섬 서비스 일부 기능이 점검 중입니다.";
    public static final String HOME_FALLBACK_MESSAGE = "현재 섬 서비스 일부 기능이 점검 중이라 기본 홈 위치로 이동합니다.";

    public static final Set<String> RESTRICTED_OPERATIONS = Set.of(
        "new-island-create",
        "inactive-island-activation",
        "island-move",
        "member-change",
        "flag-change"
    );

    public static final Set<String> CORE_WRITE_REJECTION_CODES = Set.of(
        "JOB_QUEUE_UNAVAILABLE",
        "RECOVERY_UNAVAILABLE"
    );

    private CoreApiDegradedModePolicy() {
    }

    public static boolean restrictedOperation(String operation) {
        return operation != null && RESTRICTED_OPERATIONS.contains(operation);
    }

    public static boolean coreWriteRejected(String code) {
        return code != null && CORE_WRITE_REJECTION_CODES.contains(code);
    }
}
