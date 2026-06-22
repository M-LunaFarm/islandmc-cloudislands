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

    static CoreGuiViews.PlayerProfileView guiProfile(String body) {
        return guiProfile(profile(body));
    }

    static CoreGuiViews.PlayerProfileView guiProfile(PlayerProfileView profile) {
        return new CoreGuiViews.PlayerProfileView(
            profile == null ? "" : profile.playerUuid(),
            profile == null ? "" : profile.primaryIslandId()
        );
    }

    private static String text(Map<?, ?> root, String key) {
        return SimpleJson.text(root.get(key));
    }
}
