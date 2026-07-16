package kr.lunaf.cloudislands.paper.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerFlightPreferenceRegistryTest {
    @Test
    void keepsPersistentPreferenceSeparateFromLocalFlightOwnership() {
        PlayerFlightPreferenceRegistry registry = new PlayerFlightPreferenceRegistry();
        UUID playerUuid = UUID.randomUUID();

        registry.remember(playerUuid, true);
        assertTrue(registry.known(playerUuid));
        assertTrue(registry.enabled(playerUuid));
        assertFalse(registry.managed(playerUuid));
        UUID firstUpdate = registry.beginUpdate(playerUuid);
        assertNotNull(firstUpdate);
        assertNull(registry.beginUpdate(playerUuid));
        assertTrue(registry.updateCurrent(playerUuid, firstUpdate));
        assertTrue(registry.finishUpdate(playerUuid, firstUpdate));
        assertNotNull(registry.beginUpdate(playerUuid));

        registry.markManaged(playerUuid);
        assertTrue(registry.managed(playerUuid));
        registry.clearManaged(playerUuid);
        assertTrue(registry.enabled(playerUuid));
        assertFalse(registry.managed(playerUuid));

        registry.forget(playerUuid);
        assertFalse(registry.enabled(playerUuid));
        assertFalse(registry.known(playerUuid));
        assertNotNull(registry.beginUpdate(playerUuid));
    }

    @Test
    void staleConnectionCannotFinishReplacementConnectionsUpdate() {
        PlayerFlightPreferenceRegistry registry = new PlayerFlightPreferenceRegistry();
        UUID playerUuid = UUID.randomUUID();
        UUID previousUpdate = registry.beginUpdate(playerUuid);

        registry.forget(playerUuid);
        UUID replacementUpdate = registry.beginUpdate(playerUuid);

        assertNotNull(previousUpdate);
        assertNotNull(replacementUpdate);
        assertFalse(registry.finishUpdate(playerUuid, previousUpdate));
        assertTrue(registry.updateCurrent(playerUuid, replacementUpdate));
        assertTrue(registry.finishUpdate(playerUuid, replacementUpdate));
    }
}
