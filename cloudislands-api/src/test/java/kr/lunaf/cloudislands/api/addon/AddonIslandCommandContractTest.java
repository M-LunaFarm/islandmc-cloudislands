package kr.lunaf.cloudislands.api.addon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.AddonIslandCommandSnapshot;
import org.junit.jupiter.api.Test;

class AddonIslandCommandContractTest {
    @Test
    void normalizesImmutableContextSnapshotAndResults() {
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000181");
        AddonIslandCommandContext context = new AddonIslandCommandContext(playerUuid, " island ", " NeAr ", List.of("10"));
        AddonIslandCommandSnapshot snapshot = new AddonIslandCommandSnapshot(" test-addon ", " NeAr ", List.of(" NeAr ", "NEARBY", "near"), " test.use ", " [radius] ", " Find islands ");

        assertEquals("near", context.alias());
        assertEquals(List.of("10"), context.arguments());
        assertEquals(List.of("near", "nearby"), snapshot.aliases());
        assertEquals("test.use", snapshot.permission());
        assertThrows(UnsupportedOperationException.class, () -> context.arguments().add("20"));
        assertTrue(AddonIslandCommandResult.message("ok").accepted());
        assertFalse(AddonIslandCommandResult.rejected("no").accepted());
    }
}
