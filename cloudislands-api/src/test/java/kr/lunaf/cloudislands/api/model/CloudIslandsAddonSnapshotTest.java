package kr.lunaf.cloudislands.api.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

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

    private CloudIslandsAddonSnapshot snapshot(Map<String, Boolean> features, Map<String, String> metadata) {
        return new CloudIslandsAddonSnapshot("cloudislands-satis", "CloudIslands Satis", "test", true,
                Instant.EPOCH, Instant.EPOCH, features, features, metadata);
    }
}
