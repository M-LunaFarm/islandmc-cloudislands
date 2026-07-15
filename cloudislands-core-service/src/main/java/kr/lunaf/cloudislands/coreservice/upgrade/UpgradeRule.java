package kr.lunaf.cloudislands.coreservice.upgrade;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;

public record UpgradeRule(String upgradeKey, UpgradeType type, int maxLevel, BigDecimal baseCost, BigDecimal multiplier, Map<Integer, BigDecimal> levelCosts, Map<Integer, Long> levelValues, Map<Integer, Map<String, Long>> levelItemCosts, Map<Integer, Map<String, Long>> levelEffects) {
    public UpgradeRule(String upgradeKey, UpgradeType type, int maxLevel, BigDecimal baseCost, BigDecimal multiplier) {
        this(upgradeKey, type, maxLevel, baseCost, multiplier, Map.of(), Map.of(), Map.of(), Map.of());
    }

    public UpgradeRule(String upgradeKey, UpgradeType type, int maxLevel, BigDecimal baseCost, BigDecimal multiplier, Map<Integer, Long> levelValues) {
        this(upgradeKey, type, maxLevel, baseCost, multiplier, Map.of(), levelValues, Map.of(), Map.of());
    }

    public UpgradeRule(String upgradeKey, UpgradeType type, int maxLevel, BigDecimal baseCost, BigDecimal multiplier, Map<Integer, BigDecimal> levelCosts, Map<Integer, Long> levelValues) {
        this(upgradeKey, type, maxLevel, baseCost, multiplier, levelCosts, levelValues, Map.of(), Map.of());
    }

    public UpgradeRule(String upgradeKey, UpgradeType type, int maxLevel, BigDecimal baseCost, BigDecimal multiplier, Map<Integer, BigDecimal> levelCosts, Map<Integer, Long> levelValues, Map<Integer, Map<String, Long>> levelItemCosts) {
        this(upgradeKey, type, maxLevel, baseCost, multiplier, levelCosts, levelValues, levelItemCosts, Map.of());
    }

    public UpgradeRule {
        upgradeKey = upgradeKey == null ? "" : upgradeKey.trim().toLowerCase();
        type = type == null ? UpgradePolicy.typeFor(upgradeKey) : type;
        maxLevel = Math.max(0, maxLevel);
        baseCost = nonNegative(baseCost);
        multiplier = multiplier != null && multiplier.signum() > 0 ? multiplier : BigDecimal.ONE;
        levelCosts = sanitizeCosts(levelCosts);
        levelValues = sanitizeValues(levelValues);
        levelItemCosts = sanitizeItemCosts(levelItemCosts);
        levelEffects = sanitizeEffects(levelEffects);
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

    public Map<String, Long> itemCostsForNextLevel(int currentLevel) {
        return levelItemCosts.getOrDefault(currentLevel + 1, Map.of());
    }

    public Map<String, Long> effectsForLevel(int level) {
        if (level <= 0 || levelEffects.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> effective = new LinkedHashMap<>();
        levelEffects.entrySet().stream()
            .filter(entry -> entry.getKey() <= level)
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> effective.putAll(entry.getValue()));
        return Map.copyOf(effective);
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

    private static Map<Integer, Map<String, Long>> sanitizeItemCosts(Map<Integer, Map<String, Long>> costs) {
        if (costs == null || costs.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Map<String, Long>> sanitized = new LinkedHashMap<>();
        costs.forEach((level, levelCosts) -> {
            if (level == null || level <= 0 || levelCosts == null) {
                return;
            }
            Map<String, Long> items = new LinkedHashMap<>();
            levelCosts.forEach((materialKey, amount) -> {
                String key = kr.lunaf.cloudislands.api.model.IslandWarehouseItemSnapshot.normalizeMaterialKey(materialKey);
                if (!key.isBlank() && amount != null && amount > 0L) {
                    items.put(key, amount);
                }
            });
            if (!items.isEmpty()) {
                sanitized.put(level, Map.copyOf(items));
            }
        });
        return Map.copyOf(sanitized);
    }

    private static Map<Integer, Map<String, Long>> sanitizeEffects(Map<Integer, Map<String, Long>> effects) {
        if (effects == null || effects.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Map<String, Long>> sanitized = new LinkedHashMap<>();
        effects.forEach((level, levelEffects) -> {
            if (level == null || level <= 0 || levelEffects == null) {
                return;
            }
            Map<String, Long> values = new LinkedHashMap<>();
            levelEffects.forEach((effectKey, value) -> {
                String key = effectKey == null ? "" : effectKey.trim().toLowerCase().replace('_', '-');
                if (!key.isBlank() && value != null && value >= 0L) {
                    values.put(key, value);
                }
            });
            if (!values.isEmpty()) {
                sanitized.put(level, Map.copyOf(values));
            }
        });
        return Map.copyOf(sanitized);
    }
}
