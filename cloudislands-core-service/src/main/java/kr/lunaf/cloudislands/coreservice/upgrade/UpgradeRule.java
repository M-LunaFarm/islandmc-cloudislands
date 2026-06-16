package kr.lunaf.cloudislands.coreservice.upgrade;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
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
        upgradeKey = upgradeKey == null ? "" : upgradeKey.trim().toLowerCase();
        type = type == null ? UpgradePolicy.typeFor(upgradeKey) : type;
        maxLevel = Math.max(0, maxLevel);
        baseCost = nonNegative(baseCost);
        multiplier = multiplier != null && multiplier.signum() > 0 ? multiplier : BigDecimal.ONE;
        levelCosts = sanitizeCosts(levelCosts);
        levelValues = sanitizeValues(levelValues);
    }

    public BigDecimal costForNextLevel(int currentLevel) {
        if (maxLevel <= 0 || currentLevel >= maxLevel) {
            return BigDecimal.valueOf(-1L);
        }
        BigDecimal exact = levelCosts.get(currentLevel + 1);
        if (exact != null) {
            return exact;
        }
        return nonNegative(baseCost.multiply(multiplier.pow(Math.max(0, currentLevel))));
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

    private static BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    private static Map<Integer, BigDecimal> sanitizeCosts(Map<Integer, BigDecimal> costs) {
        if (costs == null || costs.isEmpty()) {
            return Map.of();
        }
        Map<Integer, BigDecimal> sanitized = new LinkedHashMap<>();
        costs.forEach((level, cost) -> {
            if (level != null && level > 0 && cost != null && cost.signum() >= 0) {
                sanitized.put(level, cost);
            }
        });
        return Map.copyOf(sanitized);
    }

    private static Map<Integer, Long> sanitizeValues(Map<Integer, Long> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Long> sanitized = new LinkedHashMap<>();
        values.forEach((level, value) -> {
            if (level != null && level > 0 && value != null && value >= 0L) {
                sanitized.put(level, value);
            }
        });
        return Map.copyOf(sanitized);
    }
}
