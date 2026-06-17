package kr.lunaf.cloudislands.paper.cache;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import kr.lunaf.cloudislands.common.event.CacheInvalidationPlan;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;

public final class GlobalEventCacheInvalidator {
    private final LocalIslandPermissionCache permissions;
    private Consumer<AddonStateInvalidation> addonStateInvalidator = _ignored -> {};

    public GlobalEventCacheInvalidator(LocalIslandPermissionCache permissions) {
        this.permissions = permissions;
    }

    public void setAddonStateInvalidator(Consumer<AddonStateInvalidation> addonStateInvalidator) {
        this.addonStateInvalidator = addonStateInvalidator == null ? _ignored -> {} : addonStateInvalidator;
    }

    public void accept(String type, Map<String, String> fields) {
        acceptAddonState(type, fields);
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

    public void acceptAddonState(String type, Map<String, String> fields) {
        if (targetsInclude(fields, CacheInvalidationPlan.CacheTarget.ADDON_STATE)) {
            invalidateAddonState(fields);
            return;
        }
        CloudIslandEventType eventType;
        try {
            eventType = CloudIslandEventType.valueOf(type);
        } catch (IllegalArgumentException exception) {
            return;
        }
        if (CacheInvalidationPlan.targetsFor(eventType).contains(CacheInvalidationPlan.CacheTarget.ADDON_STATE)) {
            invalidateAddonState(fields);
        }
    }

    public void invalidateAllAddonState() {
        addonStateInvalidator.accept(new AddonStateInvalidation("", null));
    }

    private void invalidate(Map<String, String> fields) {
        String islandId = fields.get("islandId");
        if (islandId == null || islandId.isBlank()) {
            permissions.invalidateAll();
        } else {
            try {
                permissions.invalidate(UUID.fromString(islandId));
            } catch (IllegalArgumentException exception) {
                permissions.invalidateAll();
            }
        }
    }

    private void invalidateAddonState(Map<String, String> fields) {
        String addonId = fields.getOrDefault("addonId", "");
        addonStateInvalidator.accept(new AddonStateInvalidation(addonId, islandId(fields)));
    }

    private UUID islandId(Map<String, String> fields) {
        String islandId = fields.get("islandId");
        if (islandId == null || islandId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(islandId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean targetsInclude(Map<String, String> fields, CacheInvalidationPlan.CacheTarget target) {
        String cacheTargets = fields.getOrDefault("cacheTargets", "");
        for (String value : cacheTargets.split(",")) {
            if (value.trim().equalsIgnoreCase(target.name())) {
                return true;
            }
        }
        return false;
    }

    public record AddonStateInvalidation(String addonId, UUID islandId) {}
}
