package kr.lunaf.cloudislands.api.upgrade;

public record UpgradePurchaseSnapshot(boolean accepted, String code, String cost, IslandUpgradeSnapshot upgrade) {}
