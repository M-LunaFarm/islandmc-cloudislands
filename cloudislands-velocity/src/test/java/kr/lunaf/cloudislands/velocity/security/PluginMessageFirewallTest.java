package kr.lunaf.cloudislands.velocity.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PluginMessageFirewallTest {
    @Test
    void detectsCloudIslandsControlChannelsOnly() {
        PluginMessageFirewall firewall = new PluginMessageFirewall();

        assertTrue(firewall.isCloudIslandsPluginMessage("cloudislands"));
        assertTrue(firewall.isCloudIslandsPluginMessage("cloudislands:route"));
        assertTrue(firewall.isCloudIslandsPluginMessage("CloudIslands:admin"));
        assertFalse(firewall.isCloudIslandsPluginMessage("minecraft:brand"));
        assertFalse(firewall.isCloudIslandsPluginMessage(""));
        assertFalse(firewall.isCloudIslandsPluginMessage(null));
    }
}
