package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class CorePlayerProfileJson {
    private CorePlayerProfileJson() {
    }

    static PlayerProfileView profile(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return new PlayerProfileView(
            text(root, "playerUuid"),
            text(root, "lastName"),
            text(root, "primaryIslandId"),
            text(root, "lastSeenAt"),
            text(root, "locale")
        );
    }

    private static String text(Map<?, ?> root, String key) {
        return SimpleJson.text(root.get(key));
    }
}
