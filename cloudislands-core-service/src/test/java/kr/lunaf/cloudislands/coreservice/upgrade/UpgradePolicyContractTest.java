package kr.lunaf.cloudislands.coreservice.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertNotNull(policy.rule("fly"));
        assertEquals(UpgradeType.GENERATOR_LEVEL, UpgradePolicy.typeFor("generator:default"));
    }
}
