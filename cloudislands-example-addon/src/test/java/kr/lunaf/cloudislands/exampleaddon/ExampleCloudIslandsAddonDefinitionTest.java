package kr.lunaf.cloudislands.exampleaddon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.CloudIslandsApiContract;
import kr.lunaf.cloudislands.api.event.IslandMissionProgressEvent;
import kr.lunaf.cloudislands.api.event.RouteTicketCreatedEvent;
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
        assertTrue(addon.addonFeatures().get("commands"));
        assertTrue(addon.addonFeatures().get("gui"));
        assertTrue(addon.addonFeatures().get("custom-missions"));
        assertTrue(addon.addonFeatures().get("placeholders"));
        assertTrue(addon.addonFeatures().get("custom-menu-buttons"));
        assertTrue(addon.addonFeatures().get("custom-block-values"));
        assertEquals("example-harvest", addon.addonMissions().get(0).missionKey());
        assertEquals("example_level_goal", addon.addonPlaceholders().get(0).key());
        assertEquals("example.open", addon.addonMenuButtons().get(0).actionId());
        assertEquals("minecraft:wheat", addon.addonBlockValues().get(0).materialKey());
        assertEquals("example-harvest", certified.get("addon-mission-keys"));
        assertEquals("example_level_goal", certified.get("addon-placeholder-keys"));
        assertEquals("example.open", certified.get("addon-menu-button-actions"));
        assertEquals("minecraft:wheat", certified.get("addon-block-value-keys"));
        assertEquals("ExampleCloudIslandsEventListener", certified.get("example-event-listener"));
        assertEquals("ExampleIslandCommand", certified.get("example-command"));
        assertEquals("ExampleIslandMenuAction", certified.get("example-menu-action"));
    }

    @Test
    void exampleAddonPassesTheCertificationMatrix() {
        AddonCertificationReport report = AddonCertificationMatrix.certify(new ExampleCloudIslandsAddonDefinition("1.0.1"), CloudIslandsApiContract.metadata());

        assertTrue(report.certified(), report.failureSummary().toString());
    }

    @Test
    void pluginDescriptorHardDependsOnCloudIslands() throws Exception {
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/exampleaddon/ExampleCloudIslandsAddonPlugin.java"));

        assertTrue(plugin.contains("depend: [CloudIslands]"));
        assertTrue(plugin.contains("main: kr.lunaf.cloudislands.exampleaddon.ExampleCloudIslandsAddonPlugin"));
        assertTrue(plugin.contains("exampleisland:"));
        assertTrue(source.contains("api.capabilities()"));
        assertTrue(source.contains("CloudIslandsApiContract.WAREHOUSE_QUERY_CAPABILITY"));
        assertTrue(source.contains("new ExampleIslandCommand"));
        assertTrue(source.contains("cloudEventListener.onCloudEvent(event)"));
    }

    @Test
    void exampleEventListenerCommandAndMenuActionAreExecutableReferences() {
        ExampleCloudIslandsAddonDefinition addon = new ExampleCloudIslandsAddonDefinition("1.0.1");
        ExampleCloudIslandsEventListener listener = new ExampleCloudIslandsEventListener();
        UUID islandId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        listener.onCloudEvent(new RouteTicketCreatedEvent(
            UUID.randomUUID(),
            islandId,
            playerId,
            "visit",
            "node-a",
            "paper-a",
            "READY",
            Instant.EPOCH
        ));
        listener.onCloudEvent(new IslandMissionProgressEvent(
            islandId,
            "example-harvest",
            "BLOCK_BREAK",
            64L,
            64L,
            1L,
            true,
            Instant.EPOCH
        ));

        ExampleIslandMenuAction menuAction = new ExampleIslandMenuAction(addon.addonMenuButtons());
        assertEquals(1L, listener.observedRouteTickets());
        assertEquals(1L, listener.completedMissionEvents());
        assertEquals(OptionalValue.PRESENT, menuAction.commandFor(ExampleIslandMenuAction.ACTION_ID).isPresent() ? OptionalValue.PRESENT : OptionalValue.MISSING);
        assertTrue(menuAction.commandFor(ExampleIslandMenuAction.ACTION_ID).orElseThrow().contains("example-harvest"));
        assertTrue(listener.playerStatusLine(playerId).contains("routeTickets=1"));
        assertTrue(listener.playerStatusLine(playerId).contains("completedMissions=1"));
    }

    private enum OptionalValue {
        PRESENT,
        MISSING
    }
}
