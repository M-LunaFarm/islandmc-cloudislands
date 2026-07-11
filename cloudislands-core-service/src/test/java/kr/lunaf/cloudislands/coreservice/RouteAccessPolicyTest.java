package kr.lunaf.cloudislands.coreservice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandMetadataRepository;
import org.junit.jupiter.api.Test;

class RouteAccessPolicyTest {
    private static final UUID ISLAND_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID VISITOR_UUID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void ownerRetainsVisitAccessWhenMembershipProjectionIsMissing() {
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        metadata.setLocked(ISLAND_ID, true);

        RouteAccessDecision owner = new RouteAccessPolicy(metadata).visitAccess(OWNER_UUID, island(false));
        RouteAccessDecision visitor = new RouteAccessPolicy(metadata).visitAccess(VISITOR_UUID, island(false));

        assertTrue(owner.allowed());
        assertFalse(visitor.allowed());
    }

    @Test
    void ownerRetainsPrivateWarpAccessWhenMembershipProjectionIsMissing() {
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        metadata.setFlag(ISLAND_ID, IslandFlag.PUBLIC_WARPS, "false");

        RouteAccessDecision owner = new RouteAccessPolicy(metadata).warpAccess(OWNER_UUID, island(false), false);
        RouteAccessDecision visitor = new RouteAccessPolicy(metadata).warpAccess(VISITOR_UUID, island(false), false);

        assertTrue(owner.allowed());
        assertFalse(visitor.allowed());
    }

    @Test
    void authoritativeOwnerAccessOverridesAStaleSelfBanProjection() {
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        metadata.banVisitor(ISLAND_ID, VISITOR_UUID, OWNER_UUID, "stale migration row");
        metadata.setLocked(ISLAND_ID, true);

        RouteAccessPolicy policy = new RouteAccessPolicy(metadata);

        assertTrue(policy.visitAccess(OWNER_UUID, island(false)).allowed());
        assertTrue(policy.warpAccess(OWNER_UUID, island(false), false).allowed());
    }

    private static IslandSnapshot island(boolean publicAccess) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new IslandSnapshot(ISLAND_ID, OWNER_UUID, "owner-island", IslandState.ACTIVE, 100, 0L, "0", publicAccess, now, now);
    }
}
