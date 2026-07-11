package kr.lunaf.cloudislands.paper;

import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.RoleId;
import kr.lunaf.cloudislands.common.protection.ProtectionDecisionPolicy;
import kr.lunaf.cloudislands.common.protection.RegionIndex;
import kr.lunaf.cloudislands.paper.cache.LocalIslandPermissionCache;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionControllerTest {
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final UUID VISITOR = UUID.fromString("00000000-0000-0000-0000-000000000502");
    private static final UUID BANNED = UUID.fromString("00000000-0000-0000-0000-000000000503");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000505");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000506");
    private static final UUID TRUSTED = UUID.fromString("00000000-0000-0000-0000-000000000507");

    @Test
    void visitorFlagsDoNotBypassBannedOrMigrationState() {
        LocalIslandPermissionCache cache = new LocalIslandPermissionCache();
        ProtectionController protection = new ProtectionController(new RegionIndex(), cache);
        protection.registerIsland(ISLAND, "ci_shard_001", 0, 0, 300, 2, 3);
        cache.putFlag(ISLAND, IslandFlag.VISITOR_INTERACT, "true");
        cache.putRoleKey(ISLAND, BANNED, "BANNED");

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
        assertEquals(RoleId.of("BUILDER"), protection.roleId(ISLAND, builder));
        assertTrue(protection.checkBlock(builder, "ci_shard_001", 0, 100, 0, IslandPermission.BUILD).allowed());
        var denied = protection.checkBlock(builder, "ci_shard_001", 0, 100, 0, IslandPermission.BREAK);
        assertFalse(denied.allowed());
        assertEquals(RoleId.of("BUILDER"), denied.effectiveRoleId());
        assertNull(denied.effectiveRole());
    }

    @Test
    void blankRoleKeysNormalizeToVisitorAtTheCacheBoundary() {
        LocalIslandPermissionCache cache = new LocalIslandPermissionCache();
        ProtectionController protection = new ProtectionController(new RegionIndex(), cache);
        cache.putRoleKey(ISLAND, VISITOR, "  ");

        assertEquals(RoleId.of("VISITOR"), protection.roleId(ISLAND, VISITOR));
        assertFalse(protection.memberOrTrusted(ISLAND, VISITOR));
    }

    @Test
    void protectionSmokeMatrixCoversOwnerMemberTrustedVisitorBannedAndAdminBypass() {
        LocalIslandPermissionCache cache = new LocalIslandPermissionCache();
        ProtectionController protection = new ProtectionController(new RegionIndex(), cache);
        protection.registerIsland(ISLAND, "ci_shard_001", 0, 0, 300, 2, 3);
        cache.putRoleKey(ISLAND, OWNER, "OWNER");
        cache.putRoleKey(ISLAND, MEMBER, "MEMBER");
        cache.putRoleKey(ISLAND, TRUSTED, "TRUSTED");
        cache.putRoleKey(ISLAND, BANNED, "BANNED");
        cache.putRuleKey(ISLAND, "MEMBER", IslandPermission.BUILD, true);
        cache.putRuleKey(ISLAND, "TRUSTED", IslandPermission.OPEN_CONTAINER, true);

        assertTrue(protection.checkBlock(OWNER, "ci_shard_001", 0, 100, 0, IslandPermission.BREAK).allowed(), "owner block break must be allowed");
        assertTrue(protection.checkBlock(MEMBER, "ci_shard_001", 0, 100, 0, IslandPermission.BUILD).allowed(), "member block place must follow role allow");
        assertTrue(protection.checkBlock(TRUSTED, "ci_shard_001", 0, 100, 0, IslandPermission.OPEN_CONTAINER).allowed(), "trusted container access must follow role allow");
        assertFalse(protection.checkBlock(VISITOR, "ci_shard_001", 0, 100, 0, IslandPermission.BREAK).allowed(), "visitor block break must be denied");
        assertFalse(protection.checkBlock(BANNED, "ci_shard_001", 0, 100, 0, IslandPermission.OPEN_CONTAINER).allowed(), "banned player container access must be denied");
        assertTrue(protection.checkBlock(VISITOR, "ci_shard_001", 0, 100, 0, IslandPermission.BREAK, true).allowed(), "admin bypass must allow protected actions");
    }

    @Test
    void cropTramplingUsesBuildPermissionNotVisitorInteractFlag() throws Exception {
        String listener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandProtectionListener.java"));

        assertTrue(listener.contains("event.getAction() == Action.PHYSICAL"), "crop trampling must be classified from the physical interact event");
        assertTrue(listener.contains("event.getClickedBlock().getType() == Material.FARMLAND"), "farmland trampling must be explicitly protected");
        assertTrue(listener.contains("return IslandPermission.BUILD;"), "farmland trampling must require build permission, not visitor interact");
    }

    @Test
    void fertilizationRequiresItsOwnPermissionAndCannotGrowAcrossIslandBoundaries() throws Exception {
        String listener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandProtectionListener.java"));

        assertTrue(listener.contains("onFertilize(BlockFertilizeEvent event)"));
        assertTrue(listener.contains("IslandPermission.FERTILIZE"));
        assertTrue(listener.contains("anyMatch(state -> !sameIsland(event.getBlock(), state.getBlock()))"));
        assertTrue(listener.contains("onStructureGrow(StructureGrowEvent event)"));
        assertTrue(listener.contains("if (event.isFromBonemeal())"));
        assertTrue(listener.contains("reportBlockReplacement(state.getBlock(), state.getType())"));
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
