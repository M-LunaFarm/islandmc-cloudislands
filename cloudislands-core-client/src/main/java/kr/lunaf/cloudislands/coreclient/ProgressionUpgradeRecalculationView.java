package kr.lunaf.cloudislands.coreclient;

public record ProgressionUpgradeRecalculationView(boolean accepted, String islandId, long applied) {
    public ProgressionUpgradeRecalculationView {
        islandId = islandId == null ? "" : islandId;
        applied = Math.max(0L, applied);
    }
}
