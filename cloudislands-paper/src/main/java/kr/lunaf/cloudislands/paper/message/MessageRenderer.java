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
        return locale.isBlank() ? translations.component(key, variables) : translations.componentForLocale(locale, key, variables);
    }

    public Component componentForLocale(String locale, String key, String... variables) {
        return translations.componentForLocale(locale, key, variables);
    }

    public Component componentOrFallback(String key, String fallback, String... variables) {
        String rendered = plain(key, variables);
        return rendered.isBlank() ? componentText(fallback) : component(key, variables);
    }

    public Component componentForLocaleOrFallback(String locale, String key, String fallback, String... variables) {
        String rendered = plainForLocale(locale, key, variables);
        return rendered.isBlank() ? componentTextForLocale(locale, fallback) : componentForLocale(locale, key, variables);
    }

    public Component componentText(String template, String... variables) {
        return locale.isBlank() ? translations.componentText(template, variables) : translations.componentTextForLocale(locale, template, variables);
    }

    public Component componentTextForLocale(String locale, String template, String... variables) {
        return translations.componentTextForLocale(locale, template, variables);
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

    public List<Component> componentLines(String key, String... variables) {
        return locale.isBlank() ? translations.componentLines(key, variables) : translations.componentLinesForLocale(locale, key, variables);
    }

    public List<Component> componentLinesForLocale(String locale, String key, String... variables) {
        return translations.componentLinesForLocale(locale, key, variables);
    }
}
