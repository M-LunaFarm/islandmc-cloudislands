package kr.lunaf.cloudislands.coreclient;

import java.util.List;

public record PermissionMatrixView(
    String version,
    List<CoreGuiViews.PermissionRuleView> rules
) {
    public PermissionMatrixView {
        version = version == null ? "" : version.trim();
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
