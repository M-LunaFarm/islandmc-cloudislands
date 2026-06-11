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
        if (targetsInclude(fields, CacheInvalidationPlan.CacheTarget.PERMISSIONS)) {
            invalidate(fields);
            return;
        }
        CloudIslandEventType eventType;
        try {
            eventType = CloudIslandEventType.valueOf(type);
        } catch (IllegalArgumentException exception) {
            return;
        }
        if (CacheInvalidationPlan.targetsFor(eventType).contains(CacheInvalidationPlan.CacheTarget.PERMISSIONS)) {
            invalidate(fields);
        }
    }

    private void invalidate(Map<String, String> fields) {
        String islandId = fields.get("islandId");
        if (islandId == null || islandId.isBlank()) {
            permissions.invalidateAll();
        } else {
            permissions.invalidate(UUID.fromString(islandId));
        }
    }

    private boolean targetsInclude(Map<String, String> fields, CacheInvalidationPlan.CacheTarget target) {
        String cacheTargets = fields.getOrDefault("cacheTargets", "");
        for (String value : cacheTargets.split(",")) {
            if (value.trim().equals(target.name())) {
                return true;
            }
        }
        return false;
    }
}
