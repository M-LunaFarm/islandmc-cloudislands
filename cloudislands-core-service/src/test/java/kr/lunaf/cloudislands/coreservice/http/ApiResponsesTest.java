package kr.lunaf.cloudislands.coreservice.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import org.junit.jupiter.api.Test;

class ApiResponsesTest {
    @Test
    void okResponseUsesStructuredJson() {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(ApiResponses.ok(true)));

        assertEquals(true, root.get("accepted"));
    }

    @Test
    void errorResponseEscapesDetailsThroughJsonRenderer() {
        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        details.put("target\"node", "island, \"east\"");
        details.put("empty", null);

        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(ApiResponses.error("BAD\"CODE", "Broken, \"message\"", details)));
        Map<?, ?> error = SimpleJson.object(root.get("error"));
        Map<?, ?> renderedDetails = SimpleJson.object(error.get("details"));

        assertEquals("BAD\"CODE", SimpleJson.text(error.get("code")));
        assertEquals("Broken, \"message\"", SimpleJson.text(error.get("message")));
        assertEquals("island, \"east\"", SimpleJson.text(renderedDetails.get("target\"node")));
        assertEquals("", SimpleJson.text(renderedDetails.get("empty")));
    }
}
