package kr.lunaf.cloudislands.coreservice.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class JsonFieldsTest {
    @Test
    void malformedJsonMapsToInvalidJson() {
        CoreHttpException exception = assertThrows(CoreHttpException.class, () -> JsonFields.text("{\"name\"", "name", ""));

        assertEquals(400, exception.status());
        assertEquals("INVALID_JSON", exception.code());
    }

    @Test
    void trailingJsonMapsToInvalidJson() {
        CoreHttpException exception = assertThrows(CoreHttpException.class, () -> JsonFields.integer("{\"value\":1} true", "value", 0));

        assertEquals(400, exception.status());
        assertEquals("INVALID_JSON", exception.code());
    }

    @Test
    void invalidTypesMapToInvalidRequest() {
        CoreHttpException exception = assertThrows(CoreHttpException.class, () -> JsonFields.uuid("{\"playerUuid\":42}", "playerUuid", new UUID(0L, 0L)));

        assertEquals(400, exception.status());
        assertEquals("INVALID_REQUEST", exception.code());
    }

    @Test
    void numericOverflowMapsToInvalidRequest() {
        CoreHttpException exception = assertThrows(CoreHttpException.class, () -> JsonFields.integer("{\"maxJobs\":2147483648}", "maxJobs", 4));

        assertEquals(400, exception.status());
        assertEquals("INVALID_REQUEST", exception.code());
    }

    @Test
    void readsValidUnicodeAndUuidValues() {
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000321");
        String json = "{\"name\":\"섬\",\"playerUuid\":\"" + playerUuid + "\"}";

        assertEquals("섬", JsonFields.text(json, "name", ""));
        assertEquals(playerUuid, JsonFields.uuid(json, "playerUuid", new UUID(0L, 0L)));
    }
}
