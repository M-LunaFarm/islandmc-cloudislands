package kr.lunaf.cloudislands.common.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimpleJsonTest {
    @Test
    void stringifiesObjectsListsAndEscapedText() {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "a}");
        item.put("enabled", true);
        root.put("name", "Island \"A\"");
        root.put("items", List.of(item));
        root.put("count", 2L);

        assertEquals("{\"name\":\"Island \\\"A\\\"\",\"items\":[{\"id\":\"a}\",\"enabled\":true}],\"count\":2}", SimpleJson.stringify(root));
    }
}
