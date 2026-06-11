package kr.lunaf.cloudislands.paper.cache;

import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.common.event.CacheInvalidationPlan;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;

public final class GlobalEventCacheInvalidator {
    private final LocalIslandPermissionCache permissions;

    public GlobalEventCacheInvalidator(LocalIslandPermissionCache permissions) {
        this.permissions = permissions;
    }

    public void accept(String type, Map<String, String> fields) {
        CloudIslandEventType eventType = CloudIslandEventType.valueOf(type);
        if (CacheInvalidationPlan.targetsFor(eventType).contains(CacheInvalidationPlan.CacheTarget.PERMISSIONS)) {
            String islandId = fields.get("islandId");
            if (islandId == null || islandId.isBlank()) {
                permissions.invalidateAll();
            } else {
                permissions.invalidate(UUID.fromString(islandId));
            }
        }
    }
}
