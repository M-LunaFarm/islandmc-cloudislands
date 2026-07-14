package kr.lunaf.cloudislands.paper.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.paper.config.PaperRuntimeConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class ConfiguredMessageComponentsTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void rendersFormattingTagsAndLiteralDynamicValues() {
        MessageRenderer renderer = renderer(Map.of(
            "welcome", "<gradient:red:blue>{player}</gradient> <bold>환영합니다</bold>"
        ));

        Component component = renderer.component("welcome", "player", "<click:run_command:'/op me'>Luna</click>");

        assertEquals("<click:run_command:'/op me'>Luna</click> 환영합니다", PLAIN.serialize(component));
        assertTrue(hasColor(component), "configured gradient must produce colored Adventure components");
        assertFalse(hasClickEvent(component), "dynamic values must never become executable MiniMessage tags");
    }

    @Test
    void leavesExecutableConfiguredTagsInert() {
        MessageRenderer renderer = renderer(Map.of(
            "unsafe", "<click:run_command:'/stop'><red>관리 메시지</red></click>"
        ));

        Component component = renderer.component("unsafe");

        assertNull(component.clickEvent());
        assertFalse(hasClickEvent(component));
        assertTrue(PLAIN.serialize(component).contains("관리 메시지"));
    }

    @Test
    void preservesLocaleFallbackAndPlainStringCompatibility() {
        MessageRenderer renderer = renderer(Map.of("join-message", "<green>{player}</green> joined"));

        assertEquals("<green>Luna</green> joined", renderer.plainForLocale("en_us", "join-message", "player", "Luna"));
        assertEquals("Luna joined", PLAIN.serialize(renderer.componentForLocale("en_us", "join-message", "player", "Luna")));
    }

    @Test
    void rendersConfiguredScoreboardLinesAsComponents() {
        PaperRuntimeConfig.Messages config = new PaperRuntimeConfig.Messages(
            "ko_kr",
            Map.of(),
            List.of("<gold>플레이어:</gold> {player}", "<aqua>접속:</aqua> {online}")
        );
        MessageRenderer renderer = new MessageRenderer(TranslationManager.fromSnapshot(config, "CloudIslands"));

        List<Component> lines = renderer.componentLinesForLocale("ko_kr", "scoreboard-lines", "player", "<red>Luna", "online", "4");

        assertEquals(List.of("플레이어: <red>Luna", "접속: 4"), lines.stream().map(PLAIN::serialize).toList());
        assertTrue(lines.stream().anyMatch(ConfiguredMessageComponentsTest::hasColor));
        assertTrue(lines.stream().noneMatch(ConfiguredMessageComponentsTest::hasClickEvent));
    }

    private static MessageRenderer renderer(Map<String, String> translations) {
        PaperRuntimeConfig.Messages config = new PaperRuntimeConfig.Messages("en_us", translations, List.of());
        return new MessageRenderer(TranslationManager.fromSnapshot(config, "CloudIslands"));
    }

    private static boolean hasClickEvent(Component component) {
        if (component.clickEvent() != null) {
            return true;
        }
        return component.children().stream().anyMatch(ConfiguredMessageComponentsTest::hasClickEvent);
    }

    private static boolean hasColor(Component component) {
        if (component.color() != null && !NamedTextColor.WHITE.equals(component.color())) {
            return true;
        }
        return component.children().stream().anyMatch(ConfiguredMessageComponentsTest::hasColor);
    }
}
