package kr.lunaf.cloudislands.paper.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocaleMessageCatalogTest {
    @Test
    void loadsBundledConfigV2LocaleMessages() {
        Map<String, Map<String, String>> locales = LocaleMessageCatalog.bundledTranslations();

        assertEquals("Loading.", locales.get("en_us").get("menu.loading"));
        assertEquals("불러오는 중입니다.", locales.get("ko_kr").get("menu.loading"));
    }

    @Test
    void parsesNestedScalarYamlIntoFlatKeys() throws Exception {
        String yaml = """
            menu:
              retry: "Retry"
            errors:
              CORE_API_TIMEOUT: "Service unavailable."
            """;

        Map<String, String> values = LocaleMessageCatalog.parseFlatYaml(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertEquals("Retry", values.get("menu.retry"));
        assertEquals("Service unavailable.", values.get("errors.core-api-timeout"));
    }
}
