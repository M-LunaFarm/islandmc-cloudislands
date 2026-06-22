package kr.lunaf.cloudislands.paper;

import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RoleId;
import kr.lunaf.cloudislands.common.protection.ProtectionDecisionPolicy;
import kr.lunaf.cloudislands.common.protection.RegionIndex;
import kr.lunaf.cloudislands.paper.cache.LocalIslandPermissionCache;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionControllerTest {
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final UUID VISITOR = UUID.fromString("00000000-0000-0000-0000-000000000502");
    private static final UUID BANNED = UUID.fromString("00000000-0000-0000-0000-000000000503");

    @Test
    void visitorFlagsDoNotBypassBannedOrMigrationState() {
        LocalIslandPermissionCache cache = new LocalIslandPermissionCache();
        ProtectionController protection = new ProtectionController(new RegionIndex(), cache);
        protection.registerIsland(ISLAND, "ci_shard_001", 0, 0, 300, 2, 3);
        cache.putFlag(ISLAND, IslandFlag.VISITOR_INTERACT, "true");
        cache.putRole(ISLAND, BANNED, IslandRole.BANNED);

        assertTrue(protection.checkBlock(VISITOR, "ci_shard_001", 0, 100, 0, IslandPermission.INTERACT).allowed());
        assertFalse(protection.checkBlock(BANNED, "ci_shard_001", 0, 100, 0, IslandPermission.INTERACT).allowed());

        protection.markMigrating(ISLAND);

        var result = protection.checkBlock(VISITOR, "ci_shard_001", 0, 100, 0, IslandPermission.INTERACT);
        assertFalse(result.allowed());
        assertEquals("ISLAND_MIGRATING", result.reason());
    }

    @Test
    void exposesSynchronousHotPathPolicy() {
        ProtectionController protection = new ProtectionController(new RegionIndex(), new LocalIslandPermissionCache());

        assertEquals(ProtectionDecisionPolicy.HOT_PATH_POLICY, protection.synchronousDecisionPolicy());
        assertEquals("no-core-api-http-database-or-redis-call-on-bukkit-event-thread", ProtectionDecisionPolicy.NO_SYNC_IO_POLICY);
    }

    @Test
    void dynamicRoleKeysCanGrantProtectionPermissions() {
        UUID builder = UUID.fromString("00000000-0000-0000-0000-000000000504");
        LocalIslandPermissionCache cache = new LocalIslandPermissionCache();
        ProtectionController protection = new ProtectionController(new RegionIndex(), cache);
        protection.registerIsland(ISLAND, "ci_shard_001", 0, 0, 300, 2, 3);
        cache.putRoleKey(ISLAND, builder, "builder");
        cache.putRuleKey(ISLAND, "builder", IslandPermission.BUILD, true);
        cache.putRuleKey(ISLAND, "builder", IslandPermission.BREAK, false);

        assertTrue(protection.memberOrTrusted(ISLAND, builder));
        assertTrue(protection.checkBlock(builder, "ci_shard_001", 0, 100, 0, IslandPermission.BUILD).allowed());
        var denied = protection.checkBlock(builder, "ci_shard_001", 0, 100, 0, IslandPermission.BREAK);
        assertFalse(denied.allowed());
        assertEquals(RoleId.of("BUILDER"), denied.effectiveRoleId());
        assertNull(denied.effectiveRole());
    }

    @Test
    void roleCatalogUsesRoleKeysForDefaultSuggestions() {
        LocalIslandPermissionCache cache = new LocalIslandPermissionCache();
        cache.putRoleDefinition(ISLAND, "builder");

        assertEquals(java.util.List.of("BUILDER", "CO_OWNER", "MEMBER", "MODERATOR", "TRUSTED"), cache.roleCatalog(ISLAND, false));
        assertTrue(cache.roleCatalog(ISLAND, true).contains("VISITOR"));
        assertFalse(cache.roleCatalog(ISLAND, false).contains("OWNER"));
        assertFalse(cache.roleCatalog(ISLAND, false).contains("BANNED"));
    }
}
