package kr.lunaf.cloudislands.coreclient;

import java.util.Map;

final class CorePlayerProfileJson {
    private CorePlayerProfileJson() {
    }

    static PlayerProfileView profile(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return new PlayerProfileView(
            CoreJson.text(root, "playerUuid"),
            CoreJson.text(root, "lastName"),
            CoreJson.text(root, "primaryIslandId"),
            CoreJson.text(root, "lastSeenAt"),
            CoreJson.text(root, "locale")
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
}
