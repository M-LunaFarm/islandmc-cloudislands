package kr.lunaf.cloudislands.api.addon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CloudIslandsAddonBootstrapTest {
    @Test
    void nullAddonRegistrationIsIgnored() {
        assertFalse(CloudIslandsAddonBootstrap.registerIfAvailable(null).join().isPresent());
    }

    @Test
    void nullAddonUnregistrationIsIgnored() {
        assertFalse(CloudIslandsAddonBootstrap.unregisterIfAvailable(null).join());
    }
}
