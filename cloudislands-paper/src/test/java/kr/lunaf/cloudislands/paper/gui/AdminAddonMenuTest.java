package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;
import org.junit.jupiter.api.Test;

class AdminAddonMenuTest {
    @Test
    void addonsSortEnabledFirstThenByDisplayName() {
        CloudIslandsAddonSnapshot disabled = addon("disabled", "Alpha", false);
        CloudIslandsAddonSnapshot second = addon("second", "Zulu", true);
        CloudIslandsAddonSnapshot first = addon("first", "Alpha", true);

        assertEquals(List.of(first, second, disabled), AdminAddonMenu.sortedAddons(List.of(disabled, second, first)));
        assertEquals(List.of(), AdminAddonMenu.sortedAddons(null));
    }

    @Test
    void addonLoreShowsConfiguredAndEffectiveRuntimeStateWithoutMultilineText() {
        CloudIslandsAddonSnapshot addon = new CloudIslandsAddonSnapshot(
            "machines\nunsafe",
            "Machines",
            "2.4.1\rrelease",
            true,
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-02T03:04:05Z"),
            Map.of("commands", true, "gui", false, "placeholders", true),
            Map.of("commands", true, "gui", false, "placeholders", true),
            Map.of()
        );

        List<String> lore = AdminAddonMenu.addonLore(addon, null);

        assertTrue(lore.stream().noneMatch(line -> line.contains("\n") || line.contains("\r")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("ID: machines unsafe")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("버전: 2.4.1 release")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("상태: 활성")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("구성 기능: 2/3")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("실효 기능: 2/3")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("명령: ON")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("GUI: OFF")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("Placeholder: ON")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("비활성화 확인")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("우클릭: 기능 관리")));
    }

    private static CloudIslandsAddonSnapshot addon(String id, String name, boolean enabled) {
        return new CloudIslandsAddonSnapshot(id, name, "1.0.0", enabled, Instant.EPOCH);
    }
}
