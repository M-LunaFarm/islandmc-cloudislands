package kr.lunaf.cloudislands.velocity.message;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;

public final class VelocityMessages {
    private final String language;
    private final Map<String, String> messages;

    private VelocityMessages(String language, Map<String, String> messages) {
        this.language = language == null || language.isBlank() ? "ko_kr" : language.toLowerCase(Locale.ROOT);
        this.messages = Map.copyOf(messages);
    }

    public static VelocityMessages defaults() {
        return from("ko_kr", Map.of());
    }

    public static VelocityMessages from(String language, Map<String, String> configured) {
        Map<String, String> values = new HashMap<>();
        values.put("player-only", "플레이어만 사용할 수 있습니다.");
        values.put("no-player-permission", "섬 명령을 사용할 권한이 없습니다.");
        values.put("no-admin-permission", "섬 관리 명령을 사용할 권한이 없습니다.");
        values.put("island-create-starting", "섬을 생성하고 있습니다.");
        values.put("island-service-maintenance", "현재 섬 서비스 일부 기능이 점검 중입니다.");
        values.put("island-create-already-has-island", "이미 섬을 보유하고 있습니다.");
        values.put("island-create-template-unavailable", "사용할 수 없는 섬 템플릿입니다.");
        values.put("island-create-locked", "섬 생성을 처리하는 중입니다. 잠시 후 다시 시도해주세요.");
        values.put("island-create-node-unavailable", "현재 섬 서비스가 혼잡합니다. 잠시 후 다시 시도해주세요.");
        values.put("island-create-failed", "섬 생성에 실패했습니다.");
        if (configured != null) {
            for (Map.Entry<String, String> entry : configured.entrySet()) {
                if (entry.getValue() != null) {
                    values.put(normalize(entry.getKey()), entry.getValue());
                }
            }
        }
        return new VelocityMessages(language, values);
    }

    public Component component(String key, String... variables) {
        return Component.text(text(key, variables));
    }

    public String text(String key, String... variables) {
        String rendered = messages.getOrDefault(normalize(key), "");
        rendered = rendered.replace("{language}", language);
        for (int index = 0; index + 1 < variables.length; index += 2) {
            rendered = rendered.replace("{" + variables[index] + "}", variables[index + 1] == null ? "" : variables[index + 1]);
        }
        return rendered;
    }

    private static String normalize(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
