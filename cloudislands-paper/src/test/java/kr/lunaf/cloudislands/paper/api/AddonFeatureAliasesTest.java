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
}
