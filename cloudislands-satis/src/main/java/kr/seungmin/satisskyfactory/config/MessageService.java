package kr.seungmin.satisskyfactory.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;

public final class MessageService {
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private final ConfigService configs;

    public MessageService(ConfigService configs) {
        this.configs = configs;
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(component(key));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(component(key, placeholders));
    }

    public void sendRaw(CommandSender sender, String key) {
        sender.sendMessage(rawComponent(key));
    }

    public void sendRaw(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(rawComponent(key, placeholders));
    }

    public String text(String key) {
        return SECTION.serialize(component(key));
    }

    public Component component(String key) {
        FileConfiguration messages = configs.file("messages.yml");
        String prefix = messages.getString("prefix", "");
        String body = messages.getString("messages." + key, key);
        return parse(prefix + body);
    }

    public Component component(String key, Map<String, String> placeholders) {
        return replace(parse(template(key, true)), placeholders);
    }

    public String raw(String key) {
        return SECTION.serialize(rawComponent(key));
    }

    public String raw(String key, Map<String, String> placeholders) {
        return SECTION.serialize(rawComponent(key, placeholders));
    }

    public Component rawComponent(String key) {
        return parse(template(key, false));
    }

    public Component rawComponent(String key, Map<String, String> placeholders) {
        return replace(parse(template(key, false)), placeholders);
    }

    public String rawPlain(String key) {
        return PLAIN.serialize(rawComponent(key));
    }

    public String rawPlain(String key, Map<String, String> placeholders) {
        return PLAIN.serialize(rawComponent(key, placeholders));
    }

    private String template(String key, boolean prefixed) {
        FileConfiguration messages = configs.file("messages.yml");
        String body = messages.getString("messages." + key, key);
        return prefixed ? messages.getString("prefix", "") + body : body;
    }

    private Component parse(String input) {
        return AMPERSAND.deserialize(input == null ? "" : input);
    }

    private Component replace(Component input, Map<String, String> placeholders) {
        Component replaced = input == null ? Component.empty() : input;
        if (placeholders == null || placeholders.isEmpty()) {
            return replaced;
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                replaced = replaced.replaceText(TextReplacementConfig.builder()
                    .matchLiteral("{" + entry.getKey() + "}")
                    .replacement(Component.text(entry.getValue()))
                    .build());
            }
        }
        return replaced;
    }
}
