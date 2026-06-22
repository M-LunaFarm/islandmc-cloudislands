package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class CoreTemplateJson {
    private CoreTemplateJson() {
    }

    static List<TemplateView> templates(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return SimpleJson.list(root.get("templates")).stream()
            .map(SimpleJson::object)
            .filter(object -> !object.isEmpty())
            .map(CoreTemplateJson::template)
            .filter(template -> !template.id().isBlank())
            .toList();
    }

    static TemplateView template(String body) {
        return template(CoreJson.object(body));
    }

    static List<CoreGuiViews.TemplateView> guiTemplates(String body) {
        return guiTemplates(templates(body));
    }

    static List<CoreGuiViews.TemplateView> guiTemplates(List<TemplateView> views) {
        return views == null ? List.of() : views.stream()
            .filter(view -> view != null && !view.id().isBlank())
            .map(view -> new CoreGuiViews.TemplateView(view.id(), view.displayName(), view.enabled(), view.minNodeVersion()))
            .toList();
    }

    private static TemplateView template(Map<?, ?> object) {
        return new TemplateView(
            SimpleJson.text(object.get("id")),
            SimpleJson.text(object.get("displayName")),
            bool(object, "enabled", false),
            SimpleJson.text(object.get("minNodeVersion"))
        );
    }

    private static boolean bool(Map<?, ?> object, String key, boolean fallback) {
        Object value = object.get(key);
        return value instanceof Boolean bool ? bool : (value == null ? fallback : Boolean.parseBoolean(SimpleJson.text(value)));
    }
}
