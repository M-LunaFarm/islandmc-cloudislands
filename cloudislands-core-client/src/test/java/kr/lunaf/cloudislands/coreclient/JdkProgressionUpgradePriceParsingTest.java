package kr.lunaf.cloudislands.coreclient;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class JdkProgressionUpgradePriceParsingTest {
    @Test
    void parsesNextBankAndItemPricesForGui() {
        CoreGuiViews.UpgradeView view = JdkProgressionQueryClient.upgradeViews("""
            {"upgrades":[{"upgradeKey":"size","type":"ISLAND_SIZE","level":0,"maxLevel":3,"nextCost":"2500","nextItemCosts":{"minecraft:diamond":4,"minecraft:emerald":2}}]}
            """).getFirst();

        assertEquals("size", view.key());
        assertEquals(0, view.level());
        assertEquals(3, view.maxLevel());
        assertEquals("2500", view.nextCost());
        assertEquals(Map.of("minecraft:diamond", "4", "minecraft:emerald", "2"), view.nextItemCosts());
    }
}
