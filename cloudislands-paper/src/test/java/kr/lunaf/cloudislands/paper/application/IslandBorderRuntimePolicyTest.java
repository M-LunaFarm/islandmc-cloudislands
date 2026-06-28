package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import org.junit.jupiter.api.Test;

class IslandBorderRuntimePolicyTest {
    private static final IslandRegion REGION = new IslandRegion(
        UUID.fromString("00000000-0000-0000-0000-000000000801"),
        "islands",
        64,
        127,
        -32,
        31,
        4,
        -2
    );

    @Test
    void appliesWorldBorderFromIslandRegionAndCoreSize() {
        IslandBorderRuntimePolicy.BorderSettings settings = IslandBorderRuntimePolicy.settings(150L, Map.of(
            IslandFlag.BORDER_VISIBLE, "true",
            IslandFlag.BORDER_COLOR, "aqua",
            IslandFlag.BORDER_WARNING_BLOCKS, "12"
        ), REGION);

        assertTrue(settings.visible());
        assertEquals(95.5D, settings.centerX());
        assertEquals(-0.5D, settings.centerZ());
        assertEquals(150.0D, settings.size());
        assertEquals(12, settings.warningDistance());
        assertEquals("aqua", settings.color());
        assertEquals("visible", settings.policy());
    }

    @Test
    void hiddenPolicySuppressesPlayerWorldBorder() {
        IslandBorderRuntimePolicy.BorderSettings settings = IslandBorderRuntimePolicy.settings(150L, Map.of(
            IslandFlag.BORDER_VISIBLE, "true",
            IslandFlag.BORDER_POLICY, "hidden"
        ), REGION);

        assertFalse(settings.visible());
        assertEquals("hidden", settings.policy());
    }

    @Test
    void normalizesPlayerFacingBorderOptions() {
        assertEquals("red", IslandBorderRuntimePolicy.normalizeColor("빨강"));
        assertEquals("aqua", IslandBorderRuntimePolicy.normalizeColor("cyan"));
        assertEquals("blue", IslandBorderRuntimePolicy.normalizeColor("unknown"));
        assertEquals("warning", IslandBorderRuntimePolicy.normalizePolicy("경고"));
        assertEquals("hidden", IslandBorderRuntimePolicy.normalizePolicy("hide"));
        assertEquals("visible", IslandBorderRuntimePolicy.normalizePolicy("anything"));
    }
}
