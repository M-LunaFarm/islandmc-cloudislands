package kr.lunaf.cloudislands.coreclient;

public record ProgressionBlockDetailView(String materialKey, long count, String totalWorth, long levelPoints) {
    public ProgressionBlockDetailView {
        materialKey = materialKey == null ? "" : materialKey;
        totalWorth = totalWorth == null || totalWorth.isBlank() ? "0" : totalWorth;
    }
}
