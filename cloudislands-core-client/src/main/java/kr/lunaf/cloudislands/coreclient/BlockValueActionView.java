package kr.lunaf.cloudislands.coreclient;

public record BlockValueActionView(boolean accepted, String code, String materialKey) {
    public BlockValueActionView {
        code = code == null ? "" : code;
        materialKey = materialKey == null ? "" : materialKey;
    }
}
