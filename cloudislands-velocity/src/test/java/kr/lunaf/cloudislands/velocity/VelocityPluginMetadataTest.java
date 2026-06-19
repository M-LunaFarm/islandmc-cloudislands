package kr.lunaf.cloudislands.velocity;

import com.velocitypowered.api.plugin.Plugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VelocityPluginMetadataTest {
    @Test
    void pluginAnnotationUsesGeneratedBuildInfoVersion() {
        Plugin metadata = CloudIslandsVelocityPlugin.class.getAnnotation(Plugin.class);

        assertNotNull(metadata);
        assertEquals("1.0.1", BuildInfo.VERSION);
        assertEquals(BuildInfo.VERSION, metadata.version());
    }
}
