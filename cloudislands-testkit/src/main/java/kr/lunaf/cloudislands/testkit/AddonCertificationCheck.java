package kr.lunaf.cloudislands.testkit;

public record AddonCertificationCheck(
    String id,
    String category,
    boolean required,
    boolean passed,
    String detail
) {
    public AddonCertificationCheck {
        id = id == null || id.isBlank() ? "unknown" : id;
        category = category == null || category.isBlank() ? "general" : category;
        detail = detail == null ? "" : detail;
    }
}
