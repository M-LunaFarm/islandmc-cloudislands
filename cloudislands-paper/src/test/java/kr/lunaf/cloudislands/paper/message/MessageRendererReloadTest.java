package kr.lunaf.cloudislands.paper.message;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.paper.config.PaperRuntimeConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageRendererReloadTest {
    @Test
    void existingLocalizedViewsObserveOneAtomicTranslationSwap() {
        MessageRenderer renderer = renderer("before");
        MessageRenderer localized = renderer.forLocale("ko_kr");

        renderer.reload(translations("after"));

        assertEquals("after", renderer.plain("reload-test-marker"));
        assertEquals("after", localized.plain("reload-test-marker"));
    }

    private static MessageRenderer renderer(String marker) {
        return new MessageRenderer(translations(marker));
    }

    private static TranslationManager translations(String marker) {
        return TranslationManager.fromSnapshot(
            new PaperRuntimeConfig.Messages("ko_kr", Map.of("reload-test-marker", marker), List.of()),
            "CloudIslands"
        );
    }
}
