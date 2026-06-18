package kr.lunaf.cloudislands.paper.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AddonFeatureAliasesTest {
    @Test
    void normalizesAliasToCanonicalFeature() {
        Map<String, String> metadata = Map.of("feature-aliases", "factories:machines,generators:resource-nodes");

        assertEquals("machines", AddonFeatureAliases.normalize(metadata, "factories"));
        assertEquals("resource-nodes", AddonFeatureAliases.normalize(metadata, "generators"));
        assertEquals("market", AddonFeatureAliases.normalize(metadata, "market"));
    }

    @Test
    void returnsAliasesForCanonicalFeature() {
        Map<String, String> metadata = Map.of("feature-aliases", "factories:machines,menus:gui");

        assertEquals(java.util.List.of("factories"), AddonFeatureAliases.aliasesFor(metadata, "machines"));
    }

    @Test
    void resolvesTransitiveFeatureDependencies() {
        Map<String, String> metadata = Map.of(
                "feature-aliases", "missions:contracts",
                "feature-dependencies", "contracts:storage,missions:contracts"
        );
        Map<String, Boolean> features = Map.of(
                "storage", false,
                "contracts", true,
                "missions", true
        );

        assertEquals(false, AddonFeatureAliases.featureEnabled(metadata, features, "contracts"));
        assertEquals(false, AddonFeatureAliases.featureEnabled(metadata, features, "missions"));
    }

    @Test
    void resolvesCombinedFeatureDependencies() {
        Map<String, String> metadata = Map.of(
                "feature-dependencies", "missions:contracts+storage,contracts:storage"
        );

        assertEquals(false, AddonFeatureAliases.featureEnabled(metadata, Map.of(
                "storage", false,
                "contracts", true,
                "missions", true
        ), "missions"));
        assertEquals(true, AddonFeatureAliases.featureEnabled(metadata, Map.of(
                "storage", true,
                "contracts", true,
                "missions", true
        ), "missions"));
    }

    @Test
    void aliasFalseStillDisablesCanonicalFeature() {
        Map<String, String> metadata = Map.of("feature-aliases", "generators:resource-nodes");
        Map<String, Boolean> features = Map.of("resource-nodes", true, "generators", false);

        assertEquals(false, AddonFeatureAliases.featureEnabled(metadata, features, "resource-nodes"));
    }
}
