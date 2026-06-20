package kr.lunaf.cloudislands.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class VelocityPlayerRoutingActionsTest {
    @Test
    void normalizesVelocityEffectiveLocaleForCoreProfiles() {
        assertEquals("ko_kr", VelocityPlayerRoutingActions.normalizedLocale(Locale.KOREA));
        assertEquals("en_us", VelocityPlayerRoutingActions.normalizedLocale(Locale.US));
        assertEquals("ko_kr", VelocityPlayerRoutingActions.normalizedLocale(null));
    }

    @Test
    void recordPlayerProfileSendsLocaleToCore() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityPlayerRoutingActions.java"));

        assertTrue(source.contains("touchPlayerProfile(player.getUniqueId(), player.getUsername(), playerLocale(player))"));
        assertTrue(source.contains("player.getEffectiveLocale()"));
    }
}
