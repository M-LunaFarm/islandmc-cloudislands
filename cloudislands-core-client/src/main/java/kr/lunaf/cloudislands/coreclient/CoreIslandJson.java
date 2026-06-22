package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class CoreIslandJson {
    private CoreIslandJson() {
    }

    static CoreGuiViews.IslandInfoView info(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return new CoreGuiViews.IslandInfoView(
            text(root, "name"),
            text(root, "state"),
            text(root, "islandId"),
            number(root, "level"),
            text(root, "worth"),
            bool(root, "publicAccess"),
            bool(root, "locked"),
            number(root, "size"),
            number(root, "border"),
            text(root, "ownerUuid"),
            text(root, "createdAt"),
            text(root, "updatedAt")
        );
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    private static long number(Map<?, ?> object, String key) {
        return SimpleJson.number(object.get(key));
    }

    private static boolean bool(Map<?, ?> object, String key) {
        Object value = object.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(SimpleJson.text(value));
    }
}
