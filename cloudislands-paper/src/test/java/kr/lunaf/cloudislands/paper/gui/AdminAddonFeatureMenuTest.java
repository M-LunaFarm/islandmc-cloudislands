package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;
import org.junit.jupiter.api.Test;

class AdminAddonFeatureMenuTest {
    @Test
    void featuresAreCanonicalizedAndDistinguishConfiguredFromEffectiveState() {
        CloudIslandsAddonSnapshot addon = new CloudIslandsAddonSnapshot(
            "machines",
            "Machines",
            "2.4.1",
            false,
            Instant.EPOCH,
            Instant.EPOCH,
            Map.of("commands", true, "gui", false, "legacy-gui", true),
            Map.of("commands", true, "gui", false, "legacy-gui", true),
            Map.of(
                "feature-aliases", "legacy-gui:gui",
                "feature-dependencies", "commands:gui"
            )
        );

        List<AdminAddonFeatureMenu.FeatureEntry> entries = AdminAddonFeatureMenu.featureEntries(addon);

        assertEquals(List.of("commands", "gui"), entries.stream().map(AdminAddonFeatureMenu.FeatureEntry::key).toList());
        assertTrue(entries.get(0).configured());
        assertFalse(entries.get(0).effective());
        assertEquals("gui", entries.get(0).dependency());
        assertFalse(entries.get(1).configured());
        assertFalse(entries.get(1).effective());
    }

    @Test
    void featureLoreHasSingleLineStateAndDependencyDetails() {
        AdminAddonFeatureMenu.FeatureEntry feature = new AdminAddonFeatureMenu.FeatureEntry(
            "commands\nunsafe",
            true,
            false,
            "gui\runsafe"
        );

        List<String> lore = AdminAddonFeatureMenu.featureLore(feature, null);

        assertTrue(lore.stream().noneMatch(line -> line.contains("\n") || line.contains("\r")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("기능: commands unsafe")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("구성 상태: ON")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("실효 상태: OFF")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("필요 기능: gui unsafe")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("비활성화 확인")));
    }
}
