package kr.lunaf.cloudislands.paper.message;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;

public final class MessageRenderer {
    private final AtomicReference<TranslationManager> translations;
    private final String locale;

    public MessageRenderer(TranslationManager translations) {
        this(new AtomicReference<>(Objects.requireNonNull(translations, "translations")), "");
    }

    private MessageRenderer(AtomicReference<TranslationManager> translations, String locale) {
        this.translations = translations;
        this.locale = locale == null ? "" : locale;
    }

    public MessageRenderer forLocale(String locale) {
        return new MessageRenderer(translations, locale);
    }

    public void reload(TranslationManager translations) {
        this.translations.set(Objects.requireNonNull(translations, "translations"));
    }

    public Component component(String key, String... variables) {
        TranslationManager current = translations.get();
        return locale.isBlank() ? current.component(key, variables) : current.componentForLocale(locale, key, variables);
    }

    public Component componentForLocale(String locale, String key, String... variables) {
        return translations.get().componentForLocale(locale, key, variables);
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
        TranslationManager current = translations.get();
        return locale.isBlank() ? current.componentText(template, variables) : current.componentTextForLocale(locale, template, variables);
    }

    public Component componentTextForLocale(String locale, String template, String... variables) {
        return translations.get().componentTextForLocale(locale, template, variables);
    }

    public String plain(String key, String... variables) {
        TranslationManager current = translations.get();
        return locale.isBlank() ? current.text(key, variables) : current.textForLocale(locale, key, variables);
    }

    public String plainForLocale(String locale, String key, String... variables) {
        return translations.get().textForLocale(locale, key, variables);
    }

    public List<String> lines(String key, String... variables) {
        TranslationManager current = translations.get();
        return locale.isBlank() ? current.lines(key, variables) : current.linesForLocale(locale, key, variables);
    }

    public List<String> linesForLocale(String locale, String key, String... variables) {
        return translations.get().linesForLocale(locale, key, variables);
    }

    public List<Component> componentLines(String key, String... variables) {
        TranslationManager current = translations.get();
        return locale.isBlank() ? current.componentLines(key, variables) : current.componentLinesForLocale(locale, key, variables);
    }

    public List<Component> componentLinesForLocale(String locale, String key, String... variables) {
        return translations.get().componentLinesForLocale(locale, key, variables);
    }
}
