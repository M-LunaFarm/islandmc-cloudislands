package kr.lunaf.cloudislands.coreservice;

import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;

public final class RouteAccessPolicy {
    private final IslandMetadataRepository metadata;

    public RouteAccessPolicy(IslandMetadataRepository metadata) {
        this.metadata = metadata;
    }

    public RouteAccessDecision visitAccess(UUID playerUuid, IslandSnapshot island) {
        if (metadata.isBanned(island.islandId(), playerUuid)) {
            return RouteAccessDecision.rejected(403, "VISITOR_BANNED", "Visitor is banned from this island");
        }
        boolean member = metadata.isMember(island.islandId(), playerUuid);
        if (metadata.isLocked(island.islandId()) && !member) {
            return RouteAccessDecision.rejected(423, "ISLAND_LOCKED", "Island is locked");
        }
        if (!metadata.isPublicAccess(island.islandId()) && !member) {
            return RouteAccessDecision.rejected(403, "ISLAND_PRIVATE", "Island is private");
        }
        return RouteAccessDecision.granted();
    }

    public RouteAccessDecision warpAccess(UUID playerUuid, IslandSnapshot island, boolean publicWarp) {
        if (metadata.isBanned(island.islandId(), playerUuid)) {
            return RouteAccessDecision.rejected(403, "VISITOR_BANNED", "Visitor is banned from this island");
        }
        boolean member = metadata.isMember(island.islandId(), playerUuid);
        if (metadata.isLocked(island.islandId()) && !member) {
            return RouteAccessDecision.rejected(423, "ISLAND_LOCKED", "Island is locked");
        }
        if (!member && (!publicWarp || !islandFlagEnabled(island.islandId(), IslandFlag.PUBLIC_WARPS))) {
            return RouteAccessDecision.rejected(403, "WARP_PRIVATE", "Island warp is private");
        }
        return RouteAccessDecision.granted();
    }

    private boolean islandFlagEnabled(UUID islandId, IslandFlag flag) {
        String value = metadata.flags(islandId).values().getOrDefault(flag, "false");
        return value.equalsIgnoreCase("true")
            || value.equalsIgnoreCase("allow")
            || value.equalsIgnoreCase("allowed")
            || value.equalsIgnoreCase("enabled")
            || value.equalsIgnoreCase("on");
    }
}
