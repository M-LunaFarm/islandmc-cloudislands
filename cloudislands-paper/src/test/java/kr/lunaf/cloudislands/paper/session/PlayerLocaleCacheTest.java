package kr.lunaf.cloudislands.paper.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerLocaleCacheTest {
    @Test
    void rememberedLocalesAreNormalized() {
        PlayerLocaleCache cache = new PlayerLocaleCache();
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");

        cache.remember(playerUuid, "EN-US");

        assertEquals("en_us", cache.locale(playerUuid, "ko_kr"));
    }
}
