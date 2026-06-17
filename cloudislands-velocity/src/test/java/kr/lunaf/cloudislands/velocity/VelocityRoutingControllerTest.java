package kr.lunaf.cloudislands.velocity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityRoutingControllerTest {
    @Test
    void hidesPhysicalIslandNodeNamesWhenConfigured() throws Exception {
        VelocityRoutingController controller = new VelocityRoutingController(
                null,
                null,
                "Lobby",
                20,
                true,
                true,
                true,
                "island",
                30
        );

        assertEquals("island-pool", displayServerName(controller, "Island-1"));
        assertEquals("island-pool", displayServerName(controller, "island-6"));
        assertEquals("island-pool", displayServerName(controller, "island_5"));
        assertEquals("Lobby", displayServerName(controller, "Lobby"));
        assertEquals("-", displayServerName(controller, ""));
        assertTrue(isIslandPoolServer(controller, "Island-1"));
        assertTrue(isIslandPoolServer(controller, "island_6"));
        assertFalse(isIslandPoolServer(controller, "Lobby"));
    }

    @Test
    void canExposeNodeNamesOnlyWhenExplicitlyConfigured() throws Exception {
        VelocityRoutingController controller = new VelocityRoutingController(
                null,
                null,
                "Lobby",
                20,
                true,
                true,
                false,
                "island",
                30
        );

        assertEquals("Island-1", displayServerName(controller, "Island-1"));
        assertEquals("island-6", displayServerName(controller, "island-6"));
    }

    private String displayServerName(VelocityRoutingController controller, String serverName) throws Exception {
        Method method = VelocityRoutingController.class.getDeclaredMethod("displayServerName", String.class);
        method.setAccessible(true);
        return (String) method.invoke(controller, serverName);
    }

    private boolean isIslandPoolServer(VelocityRoutingController controller, String serverName) throws Exception {
        Method method = VelocityRoutingController.class.getDeclaredMethod("isIslandPoolServer", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(controller, serverName);
    }
}
