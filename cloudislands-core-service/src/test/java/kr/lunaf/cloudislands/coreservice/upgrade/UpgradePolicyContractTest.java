package kr.lunaf.cloudislands.coreservice.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;
import org.junit.jupiter.api.Test;

class UpgradePolicyContractTest {
    @Test
    void exposesConfigDrivenUpgradeAndEconomyContracts() {
        assertEquals("config-driven-upgrade-rules-with-bank-withdraw-and-limit-application", UpgradePolicy.CONFIG_DRIVEN_POLICY);
        assertEquals("validate-rule-max-level-cost-withdraw-bank-before-upgrade-level-write", IslandUpgradeService.PURCHASE_POLICY);
        assertEquals("economy-bridge-or-island-bank-withdraw-before-upgrade-level-commit", IslandUpgradeService.ECONOMY_ABSTRACTION_POLICY);
        assertTrue(UpgradePolicy.SUPPORTED_TYPE_POLICY.contains("ISLAND_SIZE"));
        assertTrue(UpgradePolicy.SUPPORTED_TYPE_POLICY.contains("GENERATOR_LEVEL"));
        assertTrue(UpgradePolicy.SUPPORTED_TYPE_POLICY.contains("BANK_LIMIT"));
    }

    @Test
    void defaultRulesCoverGoalUpgradeTypes() {
        UpgradePolicy policy = new UpgradePolicy();

        assertEquals(UpgradeType.ISLAND_SIZE, policy.rule("size").type());
        assertEquals(UpgradeType.MAX_MEMBERS, policy.rule("members").type());
        assertEquals(UpgradeType.HOPPER_LIMIT, policy.rule("hoppers").type());
        assertEquals(UpgradeType.GENERATOR_LEVEL, policy.rule("generator").type());
        assertEquals(UpgradeType.BANK_LIMIT, policy.rule("bank").type());
        assertEquals(UpgradeType.BORDER_SIZE, policy.rule("border").type());
        assertEquals(UpgradeType.HOME_LIMIT, policy.rule("homes").type());
        assertEquals(UpgradeType.BIOME_UNLOCK, policy.rule("biome").type());
        assertEquals(UpgradeType.KEEP_INVENTORY_ENABLE, policy.rule("keep-inventory").type());
        assertEquals(UpgradeType.BORDER_COLOR_UNLOCK, policy.rule("border-color").type());
        assertNotNull(policy.rule("fly"));
        assertEquals(UpgradeType.GENERATOR_LEVEL, UpgradePolicy.typeFor("generator:default"));
        assertEquals(UpgradeType.MEMBER_LIMIT, UpgradePolicy.typeFor("member-limit"));
        assertEquals(UpgradeType.WARP_LIMIT, UpgradePolicy.typeFor("warp-limit"));
        assertEquals(UpgradeType.HOME_LIMIT, UpgradePolicy.typeFor("home-limit"));
        assertEquals(UpgradeType.BORDER_SIZE, UpgradePolicy.typeFor("border-size"));
        assertEquals(UpgradeType.BORDER_COLOR_UNLOCK, UpgradePolicy.typeFor("border-color-unlock"));
        assertEquals(UpgradeType.BIOME_UNLOCK, UpgradePolicy.typeFor("biome-unlock"));
        assertEquals(UpgradeType.KEEP_INVENTORY_ENABLE, UpgradePolicy.typeFor("keep-inventory"));
    }

    @Test
    void bundledConfigKeepsGoalUpgradeLevelExamples() {
        UpgradePolicy policy = ConfigUpgradePolicy.load("");

        assertEquals(UpgradeType.ISLAND_SIZE, policy.rule("size").type());
        assertEquals(3, policy.rule("size").maxLevel());
        assertEquals(new BigDecimal("0"), policy.rule("size").costForNextLevel(0));
        assertEquals(new BigDecimal("10000"), policy.rule("size").costForNextLevel(1));
        assertEquals(new BigDecimal("50000"), policy.rule("size").costForNextLevel(2));
        assertEquals(100L, policy.rule("size").limitValueForLevel(1).orElseThrow());
        assertEquals(150L, policy.rule("size").limitValueForLevel(2).orElseThrow());
        assertEquals(200L, policy.rule("size").limitValueForLevel(3).orElseThrow());

        assertEquals(UpgradeType.MAX_MEMBERS, policy.rule("members").type());
        assertEquals(new BigDecimal("25000"), policy.rule("members").costForNextLevel(1));
        assertEquals(new BigDecimal("75000"), policy.rule("members").costForNextLevel(2));
        assertEquals(3L, policy.rule("members").limitValueForLevel(1).orElseThrow());
        assertEquals(5L, policy.rule("members").limitValueForLevel(2).orElseThrow());
        assertEquals(8L, policy.rule("members").limitValueForLevel(3).orElseThrow());

        assertEquals(UpgradeType.HOPPER_LIMIT, policy.rule("hoppers").type());
        assertEquals(new BigDecimal("30000"), policy.rule("hoppers").costForNextLevel(1));
        assertEquals(50L, policy.rule("hoppers").limitValueForLevel(1).orElseThrow());
        assertEquals(100L, policy.rule("hoppers").limitValueForLevel(2).orElseThrow());

        assertEquals(UpgradeType.BORDER_SIZE, policy.rule("border").type());
        assertEquals(new BigDecimal("15000"), policy.rule("border").costForNextLevel(1));
        assertEquals(150L, policy.rule("border").limitValueForLevel(2).orElseThrow());
        assertEquals(UpgradeType.HOME_LIMIT, policy.rule("homes").type());
        assertEquals(2L, policy.rule("homes").limitValueForLevel(2).orElseThrow());
        assertEquals(UpgradeType.BIOME_UNLOCK, policy.rule("biome").type());
        assertEquals(UpgradeType.KEEP_INVENTORY_ENABLE, policy.rule("keep-inventory").type());
        assertEquals(UpgradeType.BORDER_COLOR_UNLOCK, policy.rule("border-color").type());
    }

    @Test
    void exposesGoalUpgradeTypesAndEconomyBridgeShape() throws Exception {
        for (UpgradeType type : UpgradeSystemPolicy.goalUpgradeTypes()) {
            assertTrue(UpgradePolicy.SUPPORTED_TYPE_POLICY.contains(type.name()), type.name());
        }
        assertEquals("upgrades-are-loaded-from-rules-upgrades-yaml-or-configured-override-file", UpgradeSystemPolicy.CONFIG_SOURCE_POLICY);
        assertEquals("economy-integration-is-async-withdraw-deposit-balance-abstraction", UpgradeSystemPolicy.ECONOMY_BRIDGE_POLICY);
        assertEquals(
            CompletableFuture.class,
            EconomyBridge.class.getMethod("withdraw", UUID.class, BigDecimal.class, String.class).getReturnType()
        );
        assertEquals(
            CompletableFuture.class,
            EconomyBridge.class.getMethod("deposit", UUID.class, BigDecimal.class, String.class).getReturnType()
        );
        assertEquals(
            CompletableFuture.class,
            EconomyBridge.class.getMethod("balance", UUID.class).getReturnType()
        );
    }
}
