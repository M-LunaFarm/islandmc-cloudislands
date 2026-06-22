package kr.lunaf.cloudislands.velocity.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class VelocityJsonFieldsTest {
    @Test
    void readsStringBooleanAndNumbers() {
        String body = "{\"name\":\"Island\",\"enabled\":true,\"count\":12,\"rate\":2.5}";

        assertEquals("Island", VelocityJsonFields.jsonValue(body, "name"));
        assertTrue(VelocityJsonFields.boolValue(body, "enabled"));
        assertFalse(VelocityJsonFields.boolValue(body, "missing"));
        assertEquals(12L, VelocityJsonFields.longValue(body, "count"));
        assertEquals(2.5D, VelocityJsonFields.doubleValue(body, "rate"));
    }

    @Test
    void extractsNestedArrayAndObjectValues() {
        String body = "{\"items\":[{\"id\":\"a\"},{\"nested\":{\"id\":\"b\"}}],\"meta\":{\"ok\":true,\"count\":2}}";

        assertEquals("[{\"id\":\"a\"},{\"nested\":{\"id\":\"b\"}}]", VelocityJsonFields.arrayValue(body, "items"));
        assertEquals("{\"ok\":true,\"count\":2}", VelocityJsonFields.objectValue(body, "meta"));
        assertEquals(List.of("{\"id\":\"a\"}", "{\"nested\":{\"id\":\"b\"}}"), VelocityJsonFields.objects(body, "items"));
        assertEquals(2, VelocityJsonFields.countObjects(VelocityJsonFields.arrayValue(body, "items")));
        assertEquals(12, VelocityJsonFields.matchingObjectEnd("{\"a\":{\"b\":1}}", 0));
    }

    @Test
    void readsEscapedStringsWithoutBreakingObjectBoundaries() {
        String body = "{\"name\":\"Island \\\"A\\\"\",\"items\":[{\"id\":\"a}\",\"label\":\"quote \\\" inside\"}]}";

        assertEquals("Island \"A\"", VelocityJsonFields.jsonValue(body, "name"));
        assertEquals("[{\"id\":\"a}\",\"label\":\"quote \\\" inside\"}]", VelocityJsonFields.arrayValue(body, "items"));
        assertEquals(List.of("{\"id\":\"a}\",\"label\":\"quote \\\" inside\"}"), VelocityJsonFields.objects(body, "items"));
        assertEquals(1, VelocityJsonFields.countObjects(VelocityJsonFields.arrayValue(body, "items")));
    }

    @Test
    void returnsSafeDefaultsForMissingOrInvalidInput() {
        assertEquals("", VelocityJsonFields.jsonValue(null, "name"));
        assertEquals("", VelocityJsonFields.arrayValue("{\"items\":[{\"id\":\"a\"}", "items"));
        assertEquals("", VelocityJsonFields.objectValue("{\"meta\":{\"ok\":true", "meta"));
        assertEquals(0L, VelocityJsonFields.parseLong("bad"));
        assertEquals(List.of(), VelocityJsonFields.objects("{}", "items"));
        assertEquals(0, VelocityJsonFields.countObjects(""));
        assertEquals(-1, VelocityJsonFields.matchingObjectEnd(null, 0));
    }
}
