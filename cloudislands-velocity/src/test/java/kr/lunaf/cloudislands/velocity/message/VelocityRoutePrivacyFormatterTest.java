package kr.lunaf.cloudislands.velocity.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VelocityRoutePrivacyFormatterTest {
    @Test
    void hidesRouteDiagnosticNodeFieldsWhenConfigured() {
        VelocityRoutePrivacyFormatter formatter = new VelocityRoutePrivacyFormatter(true);

        assertEquals("", formatter.routeNodeSuffix("island-2"));
        assertEquals("", formatter.routeRequestedNodeSuffix("island-3"));
        assertEquals("", formatter.routeServerSuffix("island-4"));
        assertEquals("", formatter.runtimeWorldSuffix("ci_island_shard_03"));
        assertEquals("", formatter.runtimeCellSuffix(12, 7));
        assertEquals("node-2", formatter.displayNodeName("island-3", 2));
    }

    @Test
    void exposesRouteDiagnosticNodeFieldsWhenConfigured() {
        VelocityRoutePrivacyFormatter formatter = new VelocityRoutePrivacyFormatter(false);

        assertEquals(" island-2", formatter.hiddenNodeLabel("island-2"));
        assertEquals("island-3", formatter.displayNodeName("island-3", 2));
        assertEquals(" node=island-2", formatter.routeNodeSuffix("island-2"));
        assertEquals(" requestedNode=island-3", formatter.routeRequestedNodeSuffix("island-3"));
        assertEquals(" server=island-4", formatter.routeServerSuffix("island-4"));
        assertEquals(" world=ci_island_shard_03", formatter.runtimeWorldSuffix("ci_island_shard_03"));
        assertEquals(" cell=12,7", formatter.runtimeCellSuffix(12, 7));
    }
}
