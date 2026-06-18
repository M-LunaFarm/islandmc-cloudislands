package kr.lunaf.cloudislands.common.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SatisIntegrationPolicyTest {
    @Test
    void pinsOfficialFeaturePackBoundary() {
        assertEquals(
            "cloudislands-core-owns-island-lifecycle-routing-storage-protection-and-public-api",
            SatisIntegrationPolicy.officialFeaturePackBoundaries().get("platform-layer")
        );
        assertEquals(
            "cloudislands-satis-owns-optional-machines-resource-nodes-contracts-research-market-and-placeholders",
            SatisIntegrationPolicy.officialFeaturePackBoundaries().get("content-layer")
        );
        assertEquals(
            "satis-uses-cloudislands-api-and-addon-spi-without-core-internal-repository-access",
            SatisIntegrationPolicy.officialFeaturePackBoundaries().get("coupling-rule")
        );
    }

    @Test
    void keepsCoreApiLimitedToOpaqueAddonMetadataAndState() {
        assertEquals(
            "core-api-stores-addon-id-version-feature-gates-schema-version-and-capability-metadata",
            SatisIntegrationPolicy.coreApiAddonStateBoundaries().get("metadata-contract")
        );
        assertEquals(
            "core-api-stores-opaque-table-key-value-addon-state-scoped-by-island-uuid-and-addon-id",
            SatisIntegrationPolicy.coreApiAddonStateBoundaries().get("state-contract")
        );
        assertEquals(
            "core-api-does-not-interpret-machines-factories-generators-contracts-research-or-market-rules",
            SatisIntegrationPolicy.coreApiAddonStateBoundaries().get("forbidden-content-knowledge")
        );
    }

    @Test
    void pinsNodeMoveAndAddonRemovalSafetyCriteria() {
        assertTrue(SatisIntegrationPolicy.nodeMoveRemapSteps().contains("addon-remaps-volatile-world-and-cell-references"));
        assertTrue(SatisIntegrationPolicy.failureRecoverySteps().contains("duplicate-writes-rejected-by-current-island-uuid-and-fencing-context"));
        assertTrue(SatisIntegrationPolicy.addonReconnectSteps().contains("addon-metadata-missing-does-not-block-island-load"));
        assertTrue(SatisIntegrationPolicy.completionCriteria().contains("state-survives-a-node-to-b-node-island-move"));
        assertTrue(SatisIntegrationPolicy.completionCriteria().contains("base-cloudislands-functions-survive-satis-disable-or-addon-removal"));
    }
}
