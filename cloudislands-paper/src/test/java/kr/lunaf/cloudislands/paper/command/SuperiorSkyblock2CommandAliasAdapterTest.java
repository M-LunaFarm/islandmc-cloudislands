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

        assertEquals("levelcalc", recalc.subcommand());
        assertEquals("레벨계산", recalc.displayCommand());
        assertEquals("members", team.subcommand());
        assertEquals("멤버", team.displayCommand());
        assertArrayEquals(new String[] {"values", "diamond_block"}, value.args());
        assertArrayEquals(new String[] {"home", "spawn"}, teleport.args());
        assertArrayEquals(new String[] {"warp-delete", "shop"}, delwarp.args());
        assertTrue(value.migrationMode());
        assertEquals("legacySs2Aliases=5[delwarp:1,recalc:1,team:1,teleport:1,value:1]", SuperiorSkyblock2CommandAliasAdapter.metricsLine());
    }

    @Test
    void exposesMajorLegacyAliasesForPolicyCoverage() {
        for (String alias : java.util.List.of("top", "values", "value", "counts", "recalc", "missions", "ratings", "setwarp", "delwarp", "teleport", "chest", "team", "panel", "disband", "rankup", "close", "open", "uncoop", "permissions", "border")) {
            assertTrue(SuperiorSkyblock2CommandAliasAdapter.knownAlias(alias), alias);
        }
    }
}
