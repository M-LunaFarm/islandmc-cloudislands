package kr.lunaf.cloudislands.coreclient;

public record BlockValueView(String materialKey, String worth, long levelPoints, long limit) {
    public BlockValueView {
        materialKey = materialKey == null ? "" : materialKey;
        worth = worth == null ? "0" : worth;
    }
}
