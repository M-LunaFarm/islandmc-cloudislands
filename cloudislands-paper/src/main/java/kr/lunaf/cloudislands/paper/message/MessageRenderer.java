package kr.lunaf.cloudislands.paper.message;

import java.util.List;
import net.kyori.adventure.text.Component;

public final class MessageRenderer {
    private final TranslationManager translations;
    private final String locale;

    public MessageRenderer(TranslationManager translations) {
        this(translations, "");
    }

    private MessageRenderer(TranslationManager translations, String locale) {
        this.translations = translations;
        this.locale = locale == null ? "" : locale;
    }

    public MessageRenderer forLocale(String locale) {
        return new MessageRenderer(translations, locale);
    }

    public Component component(String key, String... variables) {
        return Component.text(plain(key, variables));
    }

    public Component componentForLocale(String locale, String key, String... variables) {
        return Component.text(plainForLocale(locale, key, variables));
    }

    public String plain(String key, String... variables) {
        return locale.isBlank() ? translations.text(key, variables) : translations.textForLocale(locale, key, variables);
    }

    public String plainForLocale(String locale, String key, String... variables) {
        return translations.textForLocale(locale, key, variables);
    }

    public List<String> lines(String key, String... variables) {
        return locale.isBlank() ? translations.lines(key, variables) : translations.linesForLocale(locale, key, variables);
    }

    public List<String> linesForLocale(String locale, String key, String... variables) {
        return translations.linesForLocale(locale, key, variables);
    }
}
