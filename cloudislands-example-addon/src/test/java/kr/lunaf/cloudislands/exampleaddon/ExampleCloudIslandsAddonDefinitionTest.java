package kr.lunaf.cloudislands.exampleaddon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.lunaf.cloudislands.api.CloudIslandsApiContract;
import kr.lunaf.cloudislands.testkit.AddonCertificationMatrix;
import kr.lunaf.cloudislands.testkit.AddonCertificationReport;
import kr.lunaf.cloudislands.testkit.ApiContractVerification;
import kr.lunaf.cloudislands.testkit.ApiContractVerifier;
import org.junit.jupiter.api.Test;

class ExampleCloudIslandsAddonDefinitionTest {
    @Test
    void exampleAddonMetadataPassesTestkitCertification() {
        ExampleCloudIslandsAddonDefinition addon = new ExampleCloudIslandsAddonDefinition("1.0.1");
        Map<String, String> metadata = new LinkedHashMap<>(addon.addonStandardMetadata());
        metadata.putAll(addon.addonMetadata());
        Map<String, String> certified = ApiContractVerifier.addonCertificationMetadata(metadata, CloudIslandsApiContract.metadata());

        ApiContractVerification verification = ApiContractVerifier.verifyAddon(addon.addonId(), ApiContractVerifier.requestedApiVersion(certified), certified);

        assertTrue(verification.passed(), verification.failures().toString());
        assertEquals(ExampleCloudIslandsAddonDefinition.ADDON_ID, addon.addonId());
        assertTrue(addon.addonFeatures().get("route-events"));
    }

    @Test
    void exampleAddonPassesTheCertificationMatrix() {
        AddonCertificationReport report = AddonCertificationMatrix.certify(new ExampleCloudIslandsAddonDefinition("1.0.1"), CloudIslandsApiContract.metadata());

        assertTrue(report.certified(), report.failureSummary().toString());
    }

    @Test
    void pluginDescriptorHardDependsOnCloudIslands() throws Exception {
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));

        assertTrue(plugin.contains("depend: [CloudIslands]"));
        assertTrue(plugin.contains("main: kr.lunaf.cloudislands.exampleaddon.ExampleCloudIslandsAddonPlugin"));
    }
}
