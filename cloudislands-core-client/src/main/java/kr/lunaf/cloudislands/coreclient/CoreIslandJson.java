package kr.lunaf.cloudislands.coreclient;

import java.util.Map;

final class CoreIslandJson {
    private CoreIslandJson() {
    }

    static CoreGuiViews.IslandInfoView info(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return new CoreGuiViews.IslandInfoView(
            CoreJson.text(root, "name"),
            CoreJson.text(root, "state"),
            CoreJson.text(root, "islandId"),
            CoreJson.number(root, "level"),
            CoreJson.text(root, "worth"),
            CoreJson.bool(root, "publicAccess"),
            CoreJson.bool(root, "locked"),
            CoreJson.number(root, "size"),
            CoreJson.number(root, "border"),
            CoreJson.text(root, "ownerUuid"),
            CoreJson.text(root, "createdAt"),
            CoreJson.text(root, "updatedAt")
        );
    }
}
