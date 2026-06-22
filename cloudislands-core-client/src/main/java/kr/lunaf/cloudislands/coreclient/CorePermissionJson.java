package kr.lunaf.cloudislands.coreclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class CorePermissionJson {
    private CorePermissionJson() {
    }

    static CoreGuiViews.PermissionRulesView permissionRulesView(String body) {
        Map<?, ?> root = CoreJson.object(body);
        String version = text(root, "version");
        List<CoreGuiViews.PermissionRuleView> rules = new ArrayList<>();
        for (Map<?, ?> object : CoreJson.entries(body)) {
            String role = text(object, "role");
            String permission = text(object, "permission");
            if (!role.isBlank() && !permission.isBlank()) {
                rules.add(new CoreGuiViews.PermissionRuleView(role, permission, bool(object, "allowed"), version));
            }
        }
        return new CoreGuiViews.PermissionRulesView(version, List.copyOf(rules));
    }

    static CoreGuiViews.RoleView roleView(String body) {
        return roleView(CoreJson.object(body));
    }

    static List<CoreGuiViews.RoleView> roleViews(String body) {
        List<CoreGuiViews.RoleView> roles = new ArrayList<>();
        for (Map<?, ?> object : CoreJson.entries(body)) {
            CoreGuiViews.RoleView role = roleView(object);
            if (!role.role().isBlank()) {
                roles.add(role);
            }
        }
        return List.copyOf(roles);
    }

    private static CoreGuiViews.RoleView roleView(Map<?, ?> object) {
        String role = text(object, "role");
        if (role.isBlank()) {
            role = text(object, "roleKey");
        }
        return new CoreGuiViews.RoleView(role, intValue(object, "weight"), text(object, "displayName"));
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    private static int intValue(Map<?, ?> object, String key) {
        return (int) SimpleJson.number(object.get(key));
    }

    private static boolean bool(Map<?, ?> object, String key) {
        Object value = object.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(SimpleJson.text(value));
    }
}
