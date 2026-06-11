package kr.lunaf.cloudislands.paper.upgrade;

import java.math.BigDecimal;

public record UpgradeCost(String upgradeKey, int fromLevel, int toLevel, BigDecimal cost) {}
