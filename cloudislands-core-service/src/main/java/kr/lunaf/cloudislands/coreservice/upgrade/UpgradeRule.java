package kr.lunaf.cloudislands.coreservice.upgrade;

import java.math.BigDecimal;
import java.util.Map;
import java.util.OptionalLong;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;

public record UpgradeRule(String upgradeKey, UpgradeType type, int maxLevel, BigDecimal baseCost, BigDecimal multiplier, Map<Integer, BigDecimal> levelCosts, Map<Integer, Long> levelValues) {
    public UpgradeRule(String upgradeKey, UpgradeType type, int maxLevel, BigDecimal baseCost, BigDecimal multiplier) {
        this(upgradeKey, type, maxLevel, baseCost, multiplier, Map.of(), Map.of());
    }

    public UpgradeRule(String upgradeKey, UpgradeType type, int maxLevel, BigDecimal baseCost, BigDecimal multiplier, Map<Integer, Long> levelValues) {
        this(upgradeKey, type, maxLevel, baseCost, multiplier, Map.of(), levelValues);
    }

    public UpgradeRule {
        levelCosts = levelCosts == null ? Map.of() : Map.copyOf(levelCosts);
        levelValues = levelValues == null ? Map.of() : Map.copyOf(levelValues);
    }

    public BigDecimal costForNextLevel(int currentLevel) {
        if (currentLevel >= maxLevel) {
            return BigDecimal.valueOf(-1L);
        }
        BigDecimal exact = levelCosts.get(currentLevel + 1);
        if (exact != null) {
            return exact;
        }
        return baseCost.multiply(multiplier.pow(Math.max(0, currentLevel)));
    }

    public OptionalLong limitValueForLevel(int level) {
        Long exact = levelValues.get(level);
        if (exact != null) {
            return OptionalLong.of(exact);
        }
        return levelValues.entrySet().stream()
            .filter(entry -> entry.getKey() <= level)
            .max(Map.Entry.comparingByKey())
            .map(entry -> OptionalLong.of(entry.getValue()))
            .orElseGet(OptionalLong::empty);
    }
}
