package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;

final class CoreTemplateJson {
    private CoreTemplateJson() {
    }

    static List<TemplateView> templates(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return CoreJson.objects(root, "templates").stream()
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
            CoreJson.text(object, "id"),
            CoreJson.text(object, "displayName"),
            CoreJson.bool(object, "enabled", false),
            CoreJson.text(object, "minNodeVersion")
        );
    }
}
