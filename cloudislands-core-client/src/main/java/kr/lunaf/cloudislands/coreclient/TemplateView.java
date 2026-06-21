package kr.lunaf.cloudislands.coreclient;

public record TemplateView(String id, String displayName, boolean enabled, String minNodeVersion) {
    public TemplateView {
        id = id == null ? "" : id;
        displayName = displayName == null ? "" : displayName;
        minNodeVersion = minNodeVersion == null ? "" : minNodeVersion;
    }
}
