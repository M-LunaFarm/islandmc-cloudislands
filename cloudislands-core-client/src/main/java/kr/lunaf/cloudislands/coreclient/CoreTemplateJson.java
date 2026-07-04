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

    static TemplateBundleVerificationView bundleVerification(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return new TemplateBundleVerificationView(
            CoreJson.bool(root, "ok", false),
            CoreJson.text(root, "templateId"),
            CoreJson.text(root, "bundleStoragePath"),
            CoreJson.text(root, "bundleChecksum"),
            CoreJson.number(root, "bundleSizeBytes")
        );
    }

    static boolean accepted(String body) {
        return CoreJson.accepted(CoreJson.object(body));
    }

    static List<CoreGuiViews.TemplateView> guiTemplates(String body) {
        return guiTemplates(templates(body));
    }

    static List<CoreGuiViews.TemplateView> guiTemplates(List<TemplateView> views) {
        return views == null ? List.of() : views.stream()
            .filter(view -> view != null && !view.id().isBlank())
            .map(view -> new CoreGuiViews.TemplateView(view.id(), view.displayName(), view.description(), view.category(), view.enabled(), view.minNodeVersion(), view.requiredPermission(), view.iconMaterial(), view.iconCustomModelData(), view.previewImageKey(), view.bundleStoragePath(), view.bundleChecksum(), view.bundleSizeBytes(), view.schemaVersion(), view.defaultIslandSize(), view.spawnWorldOffsetX(), view.spawnWorldOffsetY(), view.spawnWorldOffsetZ(), view.spawnYaw(), view.spawnPitch(), view.homeName(), view.environmentPreset(), view.biomeKey(), view.borderColor(), view.bankInitialBalance(), view.creationCost(), view.sortOrder(), view.tags()))
            .toList();
    }

    private static TemplateView template(Map<?, ?> object) {
        return new TemplateView(
            CoreJson.text(object, "id"),
            CoreJson.text(object, "displayName"),
            CoreJson.text(object, "description"),
            CoreJson.text(object, "category"),
            CoreJson.bool(object, "enabled", false),
            CoreJson.text(object, "minNodeVersion"),
            CoreJson.text(object, "requiredPermission"),
            CoreJson.text(object, "iconMaterial"),
            (int) CoreJson.number(object, "iconCustomModelData"),
            CoreJson.text(object, "previewImageKey"),
            CoreJson.text(object, "bundleStoragePath"),
            CoreJson.text(object, "bundleChecksum"),
            CoreJson.number(object, "bundleSizeBytes"),
            number(object, "schemaVersion", 3),
            number(object, "defaultIslandSize", 300),
            decimal(object, "spawnWorldOffsetX", 0.5D),
            decimal(object, "spawnWorldOffsetY", 100.0D),
            decimal(object, "spawnWorldOffsetZ", 0.5D),
            (float) decimal(object, "spawnYaw", 180.0D),
            (float) decimal(object, "spawnPitch", 0.0D),
            text(object, "homeName", "default"),
            text(object, "environmentPreset", "normal"),
            text(object, "biomeKey", "minecraft:plains"),
            text(object, "borderColor", "BLUE"),
            text(object, "bankInitialBalance", "0"),
            text(object, "creationCost", "0"),
            (int) CoreJson.number(object, "sortOrder"),
            CoreJson.strings(object, "tags")
        );
    }

    private static int number(Map<?, ?> object, String key, int fallback) {
        return object.containsKey(key) ? (int) CoreJson.number(object, key) : fallback;
    }

    private static double decimal(Map<?, ?> object, String key, double fallback) {
        return object.containsKey(key) ? CoreJson.decimal(object, key) : fallback;
    }

    private static String text(Map<?, ?> object, String key, String fallback) {
        return object.containsKey(key) ? CoreJson.text(object, key) : fallback;
    }
}
