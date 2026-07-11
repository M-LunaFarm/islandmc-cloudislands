package kr.seungmin.satisskyfactory.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MessageServiceTest {
    @Test
    void rendersAdventureComponentsAndKeepsExplicitLegacyBoundary() throws Exception {
        MessageService messages = service();

        assertEquals(
            "[Factory] Hello Alex",
            PlainTextComponentSerializer.plainText().serialize(messages.component("hello", Map.of("name", "Alex")))
        );
        assertEquals(
            "Hello Alex",
            PlainTextComponentSerializer.plainText().serialize(messages.rawComponent("hello", Map.of("name", "Alex")))
        );
        assertEquals("Hello Alex", messages.rawPlain("hello", Map.of("name", "Alex")));
        assertEquals(
            "§aHello Alex",
            LegacyComponentSerializer.legacySection().serialize(messages.rawComponent("hello", Map.of("name", "Alex")))
        );
        assertFalse(messages.raw("hello").contains("&a"));
        assertEquals(
            "Hello &cNot red",
            PlainTextComponentSerializer.plainText().serialize(messages.rawComponent("hello", Map.of("name", "&cNot red")))
        );
        assertFalse(
            LegacyComponentSerializer.legacySection()
                .serialize(messages.rawComponent("hello", Map.of("name", "&cNot red")))
                .contains("§cNot red"),
            "placeholder values must not be interpreted as legacy formatting"
        );
    }

    @Test
    void playerMessageBoundaryDoesNotUseDeprecatedBukkitColors() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/config/MessageService.java"));

        assertFalse(source.contains("org.bukkit.ChatColor"));
        assertFalse(source.contains("translateAlternateColorCodes"));
        assertFalse(source.contains("sender.sendMessage(text"));
    }

    @SuppressWarnings("unchecked")
    private MessageService service() throws Exception {
        ConfigService configs = new ConfigService(null);
        Field field = ConfigService.class.getDeclaredField("configs");
        field.setAccessible(true);
        Map<String, FileConfiguration> files = (Map<String, FileConfiguration>) field.get(configs);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("prefix", "&8[Factory] ");
        yaml.set("messages.hello", "&aHello {name}");
        files.put("messages.yml", yaml);
        return new MessageService(configs);
    }
}
