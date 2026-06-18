package kr.lunaf.cloudislands.common.protection;

import java.util.List;

public final class ProtectionSystemPolicy {
    public static final String REGION_INDEX_STRUCTURE =
            "world-name-to-chunk-key-to-island-region-candidates";
    public static final String LOOKUP_PIPELINE =
            "world-block-coordinate>chunk-key>region-candidates>bounding-box>island-id>local-permission-cache";
    public static final String BORDER_POLICY =
            "visitor-to-visitor-spawn-member-to-island-spawn-admin-bypass";

    private static final List<String> REGION_LOOKUP_STEPS = List.of(
            "read-world-and-block-xz",
            "build-chunk-key",
            "find-region-candidates-by-world-chunk",
            "filter-by-bounding-box",
            "resolve-island-id",
            "check-local-permission-cache"
    );

    private static final List<String> REQUIRED_PROTECTED_EVENTS = List.of(
            "BlockBreakEvent",
            "BlockPlaceEvent",
            "BlockMultiPlaceEvent",
            "PlayerInteractEvent",
            "PlayerBucketEmptyEvent",
            "PlayerBucketFillEvent",
            "InventoryOpenEvent",
            "InventoryClickEvent",
            "EntityDamageByEntityEvent",
            "EntityExplodeEvent",
            "BlockExplodeEvent",
            "HangingBreakByEntityEvent",
            "HangingPlaceEvent",
            "PlayerDropItemEvent",
            "EntityPickupItemEvent",
            "PlayerArmorStandManipulateEvent",
            "PlayerShearEntityEvent",
            "PlayerLeashEntityEvent",
            "PlayerUnleashEntityEvent",
            "VehicleDestroyEvent",
            "BlockIgniteEvent",
            "BlockBurnEvent",
            "BlockSpreadEvent",
            "LeavesDecayEvent",
            "FluidLevelChangeEvent",
            "BlockFromToEvent"
    );

    private ProtectionSystemPolicy() {
    }

    public static List<String> regionLookupSteps() {
        return REGION_LOOKUP_STEPS;
    }

    public static List<String> requiredProtectedEvents() {
        return REQUIRED_PROTECTED_EVENTS;
    }

    public static boolean requiredProtectedEvent(String eventName) {
        return REQUIRED_PROTECTED_EVENTS.contains(eventName);
    }

    public static String borderAction(String role) {
        return ProtectionDecisionPolicy.borderAction(role);
    }
}
