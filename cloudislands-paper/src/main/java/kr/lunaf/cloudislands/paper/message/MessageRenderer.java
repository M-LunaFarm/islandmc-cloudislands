package kr.lunaf.cloudislands.paper.message;

import java.util.List;
import net.kyori.adventure.text.Component;

public final class MessageRenderer {
    private final TranslationManager translations;

    public MessageRenderer(TranslationManager translations) {
        this.translations = translations;
    }

    public Component component(String key, String... variables) {
        return Component.text(plain(key, variables));
    }

    public String plain(String key, String... variables) {
        return translations.text(key, variables);
    }

    public List<String> lines(String key, String... variables) {
        return translations.lines(key, variables);
    }
}
