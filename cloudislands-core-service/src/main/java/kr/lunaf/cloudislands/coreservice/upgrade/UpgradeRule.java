package kr.lunaf.cloudislands.coreservice.upgrade;

import java.math.BigDecimal;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;

public record UpgradeRule(String upgradeKey, UpgradeType type, int maxLevel, BigDecimal baseCost, BigDecimal multiplier) {
    public BigDecimal costForNextLevel(int currentLevel) {
        if (currentLevel >= maxLevel) {
            return BigDecimal.valueOf(-1L);
        }
        return baseCost.multiply(multiplier.pow(Math.max(0, currentLevel)));
    }
}
