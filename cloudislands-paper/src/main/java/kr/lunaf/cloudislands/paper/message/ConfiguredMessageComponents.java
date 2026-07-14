package kr.lunaf.cloudislands.paper.message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

/** Renders administrator-owned message templates without enabling executable tags. */
final class ConfiguredMessageComponents {
    private static final TagResolver SAFE_TAGS = TagResolver.builder()
        .resolver(StandardTags.color())
        .resolver(StandardTags.decorations())
        .resolver(StandardTags.gradient())
        .resolver(StandardTags.rainbow())
        .resolver(StandardTags.transition())
        .resolver(StandardTags.reset())
        .resolver(StandardTags.newline())
        .resolver(StandardTags.hoverEvent())
        .resolver(StandardTags.keybind())
        .resolver(StandardTags.translatable())
        .resolver(StandardTags.translatableFallback())
        .resolver(StandardTags.font())
        .resolver(StandardTags.pride())
        .resolver(StandardTags.shadowColor())
        .resolver(StandardTags.sprite())
        .build();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder().tags(SAFE_TAGS).build();

    private ConfiguredMessageComponents() {
    }

    static Component render(String template, String serviceName, String locale, String... variables) {
        String source = template == null ? "" : template;
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("service", serviceName == null ? "" : serviceName);
        values.put("locale", locale == null ? "" : locale);
        if (variables != null) {
            for (int index = 0; index + 1 < variables.length; index += 2) {
                if (variables[index] != null) {
                    values.put(variables[index], variables[index + 1] == null ? "" : variables[index + 1]);
                }
            }
        }

        List<TagResolver> placeholders = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String tag = "ci_var_" + index++;
            source = source.replace("{" + entry.getKey() + "}", "<" + tag + ">");
            placeholders.add(Placeholder.component(tag, Component.text(entry.getValue())));
        }
        try {
            return MINI_MESSAGE.deserialize(source, placeholders.toArray(TagResolver[]::new));
        } catch (RuntimeException ignored) {
            return Component.text(renderPlain(template, serviceName, locale, variables));
        }
    }

    private static String renderPlain(String template, String serviceName, String locale, String... variables) {
        String rendered = template == null ? "" : template;
        rendered = rendered.replace("{service}", serviceName == null ? "" : serviceName)
            .replace("{locale}", locale == null ? "" : locale);
        if (variables != null) {
            for (int index = 0; index + 1 < variables.length; index += 2) {
                if (variables[index] != null) {
                    rendered = rendered.replace("{" + variables[index] + "}", variables[index + 1] == null ? "" : variables[index + 1]);
                }
            }
        }
        return rendered;
    }
}
