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
        lines.put("scoreboard-lines", List.of("플레이어: {player}", "접속: {online}명", "섬 이동: /섬", "방문: /섬 방문", "관리: /섬 설정"));
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
        values.put("migration-notice-primary", "섬 서버를 최적화하는 중입니다...");
        values.put("migration-notice-secondary", "잠시 후 자동으로 이동됩니다.");
        values.put("migration-return-register-failed", "섬 이동 준비를 등록하지 못했습니다.");
        values.put("migration-return-start", "최적화된 섬 서버로 이동합니다.");
        values.put("migration-return-not-ready", "섬 서버 이동 준비가 완료되지 않았습니다. 잠시 후 /섬 홈을 사용해주세요.");
        values.put("island-restore-evacuate", "섬 복원을 위해 로비로 이동합니다.");
        values.put("island-reset-evacuate", "섬 리셋을 위해 로비로 이동합니다.");
        values.put("island-delete-evacuate", "섬 삭제를 위해 로비로 이동합니다.");
        values.put("island-operation-evacuate", "섬 작업을 위해 로비로 이동합니다.");
        values.put("boundary-member-return", "섬 경계 밖으로 이동할 수 없어 섬 스폰으로 돌려보냈습니다.");
        values.put("boundary-visitor-return", "섬 경계 밖으로 이동할 수 없어 방문자 위치로 돌려보냈습니다.");
        values.put("flag-fly-denied", "이 섬에서는 비행할 수 없습니다.");
        values.put("flag-pvp-denied", "이 섬에서는 PVP가 비활성화되어 있습니다.");
        values.put("route-visit-cancelled", "섬 방문이 취소되었습니다.");
        values.put("route-arrived-visit", "방문한 섬에 도착했습니다.");
        values.put("route-arrived-warp", "섬 워프에 도착했습니다.");
        values.put("route-arrived-admin", "관리자 이동이 완료되었습니다.");
        values.put("route-arrived-home", "내 섬에 도착했습니다.");
        values.put("route-consume-loading", "섬 로딩 중");
        values.put("route-consume-preparing", "섬을 준비하는 중입니다...");
        values.put("route-consume-failed", "섬 이동 준비가 완료되지 않았습니다. 다시 시도해주세요.");
        values.put("route-login-proxy-required", "정상적인 프록시 경로로 접속해주세요.");
        values.put("route-login-forwarding-not-ready", "섬 서버 보안 설정이 완료되지 않았습니다. 관리자에게 문의해주세요.");
        values.put("route-login-session-required", "정상적인 섬 입장 요청이 없습니다. /섬 홈으로 다시 이동해주세요.");
        values.put("route-session-check-failed", "섬 입장 준비를 확인하지 못했습니다.");
        values.put("route-session-preparing", "섬 입장을 준비하는 중입니다...");
        values.put("route-session-missing-fallback", "섬 입장 요청이 없어 로비로 이동합니다.");
        values.put("limit-reached", "섬 {limit} 제한에 도달했습니다. 현재 {current}/{max}");
        values.put("level-recalculate-denied", "섬 레벨을 계산할 권한이 없습니다.");
        values.put("level-recalculate-started", "섬 블록을 다시 확인하는 중입니다.");
        values.put("bank-deposit-denied", "섬 은행에 입금할 권한이 없습니다.");
        values.put("bank-withdraw-denied", "섬 은행에서 출금할 권한이 없습니다.");
        values.put("economy-unavailable", "경제 플러그인을 찾을 수 없습니다.");
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
