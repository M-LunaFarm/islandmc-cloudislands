package kr.lunaf.cloudislands.paper.message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class TranslationManager {
    private final String locale;
    private final String serviceName;
    private final Map<String, String> translations;
    private final Map<String, List<String>> lineTranslations;

    private TranslationManager(String locale, String serviceName, Map<String, String> translations, Map<String, List<String>> lineTranslations) {
        this.locale = locale == null || locale.isBlank() ? "ko_kr" : locale.toLowerCase(Locale.ROOT);
        this.serviceName = serviceName == null || serviceName.isBlank() ? "CloudIslands" : serviceName;
        this.translations = Map.copyOf(translations);
        this.lineTranslations = copyLines(lineTranslations);
    }

    public static TranslationManager fromConfig(FileConfiguration config, String serviceName) {
        Map<String, String> values = defaults(serviceName);
        ConfigurationSection section = config.getConfigurationSection("messages.translations");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null) {
                    values.put(normalize(key), value);
                }
            }
        }
        Map<String, List<String>> lines = new HashMap<>();
        lines.put("scoreboard-lines", List.of("섬 이동: /섬", "방문: /섬 방문", "관리: /섬 설정"));
        List<String> configuredScoreboard = config.getStringList("messages.scoreboard-lines");
        if (!configuredScoreboard.isEmpty()) {
            lines.put("scoreboard-lines", configuredScoreboard);
        }
        return new TranslationManager(config.getString("plugin.language", "ko_kr"), serviceName, values, lines);
    }

    public String text(String key, String... variables) {
        String template = translations.getOrDefault(normalize(key), "");
        return render(template, variables);
    }

    public List<String> lines(String key, String... variables) {
        List<String> templates = lineTranslations.getOrDefault(normalize(key), List.of());
        List<String> rendered = new ArrayList<>(templates.size());
        for (String template : templates) {
            rendered.add(render(template, variables));
        }
        return rendered;
    }

    public String locale() {
        return locale;
    }

    private String render(String template, String... variables) {
        String rendered = template == null ? "" : template;
        rendered = rendered.replace("{service}", serviceName).replace("{locale}", locale);
        for (int index = 0; index + 1 < variables.length; index += 2) {
            rendered = rendered.replace("{" + variables[index] + "}", variables[index + 1] == null ? "" : variables[index + 1]);
        }
        return rendered;
    }

    private static Map<String, String> defaults(String serviceName) {
        Map<String, String> values = new HashMap<>();
        String name = serviceName == null || serviceName.isBlank() ? "CloudIslands" : serviceName;
        values.put("tab-header", "{service}");
        values.put("tab-footer", "섬을 준비하고 있습니다. /섬 으로 이동과 관리를 시작하세요.");
        values.put("tab-player-name", "{player}");
        values.put("join-message", "{player}님이 섬 서비스에 접속했습니다.");
        values.put("quit-message", "{player}님이 섬 서비스에서 나갔습니다.");
        values.put("server-brand", "{service}");
        values.put("chat-prefix", "[" + name + "] ");
        values.put("chat-format", "{prefix}{player}: {message}");
        values.put("island-chat-format", "[섬] {actor}: {message}");
        values.put("team-chat-format", "[팀] {actor}: {message}");
        values.put("scoreboard-title", "{service}");
        values.put("route-loading-complete", "{target} 로딩 완료");
        values.put("route-loading-progress", "{target} 로딩 중 {progress}%");
        values.put("route-preparing-progress", "{target}을 준비하는 중입니다... {progress}%");
        values.put("route-ready", "잠시 후 {target}으로 이동합니다.");
        return values;
    }

    private static String normalize(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static Map<String, List<String>> copyLines(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }
}
