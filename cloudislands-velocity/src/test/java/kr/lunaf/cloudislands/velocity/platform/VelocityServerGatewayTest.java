package kr.lunaf.cloudislands.velocity.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VelocityServerGatewayTest {
    @Test
    void hidesPhysicalIslandNodeNamesWhenConfigured() {
        VelocityServerGateway gateway = new VelocityServerGateway(null, "island", true);

        assertEquals("island-pool", gateway.displayServerName("Island-1"));
        assertEquals("island-pool", gateway.displayServerName("island-6"));
        assertEquals("island-pool", gateway.displayServerName("island_5"));
        assertEquals("Lobby", gateway.displayServerName("Lobby"));
        assertEquals("-", gateway.displayServerName(""));
        assertTrue(gateway.isIslandPoolServer("Island-1"));
        assertTrue(gateway.isIslandPoolServer("island_6"));
        assertFalse(gateway.isIslandPoolServer("Lobby"));
    }

    @Test
    void canExposeNodeNamesOnlyWhenExplicitlyConfigured() {
        VelocityServerGateway gateway = new VelocityServerGateway(null, "island", false);

        assertEquals("Island-1", gateway.displayServerName("Island-1"));
        assertEquals("island-6", gateway.displayServerName("island-6"));
    }
}
