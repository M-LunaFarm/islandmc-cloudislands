package kr.lunaf.cloudislands.api.model;

public record AddonMenuButtonSnapshot(
    String menuId,
    String actionId,
    String materialKey,
    String displayName,
    String command,
    boolean enabled
) {
    public AddonMenuButtonSnapshot {
        menuId = safe(menuId, "island.main");
        actionId = safe(actionId, "");
        materialKey = safe(materialKey, "PAPER");
        displayName = safe(displayName, actionId);
        command = safe(command, "");
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
