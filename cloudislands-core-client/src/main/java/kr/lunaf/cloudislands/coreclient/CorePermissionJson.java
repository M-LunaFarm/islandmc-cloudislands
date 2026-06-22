package kr.lunaf.cloudislands.coreclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class CorePermissionJson {
    private CorePermissionJson() {
    }

    static CoreGuiViews.PermissionRulesView permissionRulesView(String body) {
        Map<?, ?> root = CoreJson.object(body);
        String version = CoreJson.text(root, "version");
        List<CoreGuiViews.PermissionRuleView> rules = new ArrayList<>();
        for (Map<?, ?> object : CoreJson.entries(body)) {
            String role = CoreJson.text(object, "role");
            String permission = CoreJson.text(object, "permission");
            if (!role.isBlank() && !permission.isBlank()) {
                rules.add(new CoreGuiViews.PermissionRuleView(role, permission, CoreJson.bool(object, "allowed"), version));
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
        String role = CoreJson.text(object, "role");
        if (role.isBlank()) {
            role = CoreJson.text(object, "roleKey");
        }
        return new CoreGuiViews.RoleView(role, (int) CoreJson.number(object, "weight"), CoreJson.text(object, "displayName"));
    }
}
