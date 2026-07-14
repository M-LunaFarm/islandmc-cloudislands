package kr.lunaf.cloudislands.paper.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import org.junit.jupiter.api.Test;

class AdminIslandInfoSectionsTest {
    private static final UUID ISLAND_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Test
    void emptyOptionalDataProducesNoEmptySectionsOrSeparators() {
        assertEquals(List.of(), AdminIslandInfoSections.collect(List.of(), List.of()));
        assertEquals(List.of(), AdminIslandInfoSections.collect(null, null));
    }

    @Test
    void effectsRoleLimitsAndUpgradesAreDeterministicAndTyped() {
        List<IslandLimitSnapshot> limits = List.of(
            limit("ROLE_LIMIT:TRUSTED", 4L),
            limit("HOPPER", 100L),
            limit("EFFECT:SPEED", 0L),
            limit("ROLE_LIMIT:MEMBER", 12L),
            limit("EFFECT:HASTE", 2L)
        );
        List<CoreGuiViews.UpgradeView> upgrades = List.of(
            new CoreGuiViews.UpgradeView("size", "SIZE", 3, "", 5, "1000", Map.of()),
            new CoreGuiViews.UpgradeView("generator", "GENERATOR", 1)
        );

        assertEquals(List.of(
            new AdminIslandInfoSections.Section(AdminIslandInfoSections.Kind.EFFECTS, "HASTE=2,SPEED=0"),
            new AdminIslandInfoSections.Section(AdminIslandInfoSections.Kind.ROLE_LIMITS, "MEMBER=12,TRUSTED=4"),
            new AdminIslandInfoSections.Section(AdminIslandInfoSections.Kind.UPGRADES, "generator=1,size=3/5")
        ), AdminIslandInfoSections.collect(limits, upgrades));
    }

    @Test
    void untrustedKeysCannotCreateBlankOrMultiLineAdminOutput() {
        List<CoreGuiViews.UpgradeView> upgrades = List.of(
            new CoreGuiViews.UpgradeView("first\n\nupgrade", "CUSTOM", 2),
            new CoreGuiViews.UpgradeView("\r\t", "CUSTOM", 9)
        );

        List<AdminIslandInfoSections.Section> sections = AdminIslandInfoSections.collect(
            List.of(limit("EFFECT:NIGHT\nVISION", 1L)),
            upgrades
        );

        assertEquals("NIGHT_VISION=1", sections.get(0).value());
        assertEquals("first upgrade=2", sections.get(1).value());
        assertFalse(sections.stream().anyMatch(section -> section.value().contains("\n") || section.value().contains("\r")));
    }

    private static IslandLimitSnapshot limit(String key, long value) {
        return new IslandLimitSnapshot(ISLAND_ID, key, value, new UUID(0L, 0L), Instant.EPOCH);
    }
}
