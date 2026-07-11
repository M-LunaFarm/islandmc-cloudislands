package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SuperiorSkyblock2CommandAliasAdapterTest {
    @AfterEach
    void resetUsage() {
        SuperiorSkyblock2CommandAliasAdapter.resetUsageForTests();
    }

    @Test
    void disabledAdapterDoesNotTranslateLegacyAliases() {
        SuperiorSkyblock2CommandAliasAdapter adapter = new SuperiorSkyblock2CommandAliasAdapter(false, true);

        assertTrue(adapter.translate(new String[] {"recalc"}).isEmpty());
        assertTrue(adapter.adminGuidance("purge").isEmpty());
        assertEquals("legacySs2Aliases=0", SuperiorSkyblock2CommandAliasAdapter.metricsLine());
    }

    @Test
    void translatesConfiguredSuperiorSkyblock2AliasesToCloudIslandsSubcommands() {
        SuperiorSkyblock2CommandAliasAdapter adapter = new SuperiorSkyblock2CommandAliasAdapter(true, true);

        SuperiorSkyblock2CommandAliasAdapter.ResolvedAlias recalc = adapter.translate(new String[] {"recalc"}).orElseThrow();
        SuperiorSkyblock2CommandAliasAdapter.ResolvedAlias team = adapter.translate(new String[] {"team"}).orElseThrow();
        SuperiorSkyblock2CommandAliasAdapter.ResolvedAlias value = adapter.translate(new String[] {"value", "diamond_block"}).orElseThrow();
        SuperiorSkyblock2CommandAliasAdapter.ResolvedAlias teleport = adapter.translate(new String[] {"teleport", "spawn"}).orElseThrow();
        SuperiorSkyblock2CommandAliasAdapter.ResolvedAlias delwarp = adapter.translate(new String[] {"delwarp", "shop"}).orElseThrow();
        SuperiorSkyblock2CommandAliasAdapter.ResolvedAlias uncoop = adapter.translate(new String[] {"uncoop", "Member"}).orElseThrow();

        assertEquals("levelcalc", recalc.subcommand());
        assertEquals("레벨계산", recalc.displayCommand());
        assertEquals("member-list-target", team.subcommand());
        assertEquals("멤버목록", team.displayCommand());
        assertArrayEquals(new String[] {"value", "diamond_block"}, value.args());
        assertArrayEquals(new String[] {"home", "spawn"}, teleport.args());
        assertArrayEquals(new String[] {"warp-delete", "shop"}, delwarp.args());
        assertArrayEquals(new String[] {"untrust", "Member"}, uncoop.args());
        assertTrue(value.migrationMode());
        assertEquals("legacySs2Aliases=6[delwarp:1,recalc:1,team:1,teleport:1,uncoop:1,value:1]", SuperiorSkyblock2CommandAliasAdapter.metricsLine());
    }

    @Test
    void exposesMajorLegacyAliasesForPolicyCoverage() {
        for (String alias : java.util.List.of("top", "values", "value", "counts", "recalc", "missions", "ratings", "setwarp", "delwarp", "teleport", "chest", "team", "coops", "panel", "disband", "rankup", "close", "open", "uncoop", "permissions", "border", "join", "balance", "bal", "money", "setbiome", "vault", "add", "remove", "lang", "manager", "cp", "setperm", "settp", "setgo", "show", "showteam", "online", "tc", "tp", "go", "leader", "leadership", "expel", "warp")) {
            assertTrue(SuperiorSkyblock2CommandAliasAdapter.knownAlias(alias), alias);
        }
    }

    @Test
    void officialPlayerAliasesWinInsteadOfBeingMisclassifiedAsAdminCommands() {
        SuperiorSkyblock2CommandAliasAdapter adapter = new SuperiorSkyblock2CommandAliasAdapter(true, true);

        assertArrayEquals(new String[] {"accept", "Owner"}, adapter.translate(new String[] {"join", "Owner"}).orElseThrow().args());
        assertArrayEquals(new String[] {"invite", "Player"}, adapter.translate(new String[] {"add", "Player"}).orElseThrow().args());
        assertArrayEquals(new String[] {"kick", "Player"}, adapter.translate(new String[] {"remove", "Player"}).orElseThrow().args());
        assertTrue(adapter.adminGuidance("join").isEmpty());
        assertTrue(adapter.adminGuidance("add").isEmpty());
        assertTrue(adapter.adminGuidance("remove").isEmpty());
        assertArrayEquals(new String[] {"bank-balance-target", "NamedIsland"}, adapter.translate(new String[] {"balance", "NamedIsland"}).orElseThrow().args());
        assertArrayEquals(new String[] {"info-target", "Player"}, adapter.translate(new String[] {"show", "Player"}).orElseThrow().args());
        assertArrayEquals(new String[] {"member-list-target", "Player"}, adapter.translate(new String[] {"showteam", "Player"}).orElseThrow().args());
        assertArrayEquals(new String[] {"legacy-warp", "Player", "shop"}, adapter.translate(new String[] {"warp", "Player", "shop"}).orElseThrow().args());
    }

    @Test
    void adminAliasesReturnCiadminGuidanceInsteadOfPlayerTranslations() {
        SuperiorSkyblock2CommandAliasAdapter adapter = new SuperiorSkyblock2CommandAliasAdapter(true, true);

        SuperiorSkyblock2CommandAliasAdapter.AdminAliasGuidance purge = adapter.adminGuidance("purge").orElseThrow();
        SuperiorSkyblock2CommandAliasAdapter.AdminAliasGuidance debug = adapter.adminGuidance("debug").orElseThrow();
        SuperiorSkyblock2CommandAliasAdapter.AdminAliasGuidance nestedPurge = adapter.adminGuidance(new String[] {"admin", "purge", "OldIsland"}).orElseThrow();

        assertEquals("purge", purge.alias());
        assertEquals("island delete <island> --confirm", purge.ciadminCommand());
        assertTrue(purge.dangerous());
        assertEquals("doctor", debug.ciadminCommand());
        assertEquals("admin purge", nestedPurge.alias());
        assertEquals("island delete <island> --confirm", nestedPurge.ciadminCommand());
        assertTrue(nestedPurge.dangerous());
        assertTrue(adapter.adminGuidance(new String[] {"ADMIN", "DEBUG"}).isPresent());
        assertTrue(adapter.adminGuidance(new String[] {"admin"}).isEmpty());
        assertTrue(adapter.adminGuidance(new String[] {"admin", "unknown"}).isEmpty());
        assertTrue(SuperiorSkyblock2CommandAliasAdapter.knownAdminAlias("cmdall"));
        assertTrue(SuperiorSkyblock2CommandAliasAdapter.knownAdminAlias("resetworld"));
        assertTrue(SuperiorSkyblock2CommandAliasAdapter.knownAdminAlias("setpermission"));
    }
}
