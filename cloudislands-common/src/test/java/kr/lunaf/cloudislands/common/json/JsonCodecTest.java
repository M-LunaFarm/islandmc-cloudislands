package kr.lunaf.cloudislands.common.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonCodecTest {
    @Test
    void rejectsMalformedObject() {
        assertInvalidJson("{\"a\" 1}");
        assertInvalidJson("{\"a\":1,");
        assertInvalidJson("{\"a\":\"unterminated}");
    }

    @Test
    void rejectsTrailingDataAndDuplicateKeys() {
        assertInvalidJson("{\"a\":1} {\"b\":2}");
        assertInvalidJson("{\"a\":1,\"a\":2}");
    }

    @Test
    void parsesUnicodeStrictly() {
        Map<String, Object> object = JsonCodec.readObject("{\"name\":\"섬\",\"snowman\":\"\\u2603\"}");

        assertEquals("섬", object.get("name"));
        assertEquals("☃", object.get("snowman"));
    }

    @Test
    void rejectsExcessiveNestingAndStringLength() {
        String nested = "[".repeat(JsonCodec.MAX_NESTING_DEPTH + 2) + "0" + "]".repeat(JsonCodec.MAX_NESTING_DEPTH + 2);
        String largeString = "{\"value\":\"" + "x".repeat(JsonCodec.MAX_STRING_LENGTH + 1) + "\"}";

        assertInvalidJson(nested);
        assertInvalidJson(largeString);
    }

    @Test
    void writesEscapedJson() {
        String json = JsonCodec.write(Map.of("text", "line\nquote\""));

        assertEquals("{\"text\":\"line\\nquote\\\"\"}", json);
    }

    private static void assertInvalidJson(String json) {
        JsonCodecException exception = assertThrows(JsonCodecException.class, () -> JsonCodec.read(json));
        assertEquals(JsonCodecException.Kind.INVALID_JSON, exception.kind());
        assertTrue(exception.getMessage().contains("JSON"));
    }
}
