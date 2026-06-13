package kr.lunaf.cloudislands.api.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudIslandsAddonSnapshotTest {
    @Test
    void resolvesFeatureAliasesFromMetadata() {
        CloudIslandsAddonSnapshot snapshot = snapshot(Map.of("machines", true),
                Map.of("feature-aliases", "factories:machines"));

        assertTrue(snapshot.featureEnabled("machines"));
        assertTrue(snapshot.featureEnabled("factories"));
    }

    @Test
    void aliasAndCanonicalUseFalseWins() {
        CloudIslandsAddonSnapshot disabledCanonical = snapshot(Map.of("machines", false, "factories", true),
                Map.of("feature-aliases", "factories:machines"));
        CloudIslandsAddonSnapshot disabledAlias = snapshot(Map.of("machines", true, "factories", false),
                Map.of("feature-aliases", "factories:machines"));

        assertFalse(disabledCanonical.featureEnabled("factories"));
        assertFalse(disabledAlias.featureEnabled("machines"));
    }

    @Test
    void fillsMissingIdentityAndTimestamps() {
        CloudIslandsAddonSnapshot snapshot = new CloudIslandsAddonSnapshot(null, "", "", true,
                null, null, null, null, null);

        assertEquals("unknown-addon", snapshot.id());
        assertEquals("unknown-addon", snapshot.displayName());
        assertEquals("unknown", snapshot.version());
        assertEquals(Instant.EPOCH, snapshot.registeredAt());
        assertEquals(Instant.EPOCH, snapshot.updatedAt());
        assertEquals(Map.of(), snapshot.configuredFeatures());
        assertEquals(Map.of(), snapshot.features());
        assertEquals(Map.of(), snapshot.metadata());
    }

    @Test
    void dropsNullMapEntries() {
        Map<String, Boolean> features = new HashMap<>();
        features.put("machines", true);
        features.put("contracts", null);
        features.put(null, false);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("mode", "ADDON");
        metadata.put("provider", null);
        metadata.put(null, "ignored");

        CloudIslandsAddonSnapshot snapshot = new CloudIslandsAddonSnapshot("addon", "Addon", "1", true,
                Instant.EPOCH, Instant.EPOCH, features, features, metadata);

        assertEquals(Map.of("machines", true), snapshot.configuredFeatures());
        assertEquals(Map.of("machines", true), snapshot.features());
        assertEquals(Map.of("mode", "ADDON"), snapshot.metadata());
    }

    private CloudIslandsAddonSnapshot snapshot(Map<String, Boolean> features, Map<String, String> metadata) {
        return new CloudIslandsAddonSnapshot("cloudislands-satis", "CloudIslands Satis", "test", true,
                Instant.EPOCH, Instant.EPOCH, features, features, metadata);
    }
}
