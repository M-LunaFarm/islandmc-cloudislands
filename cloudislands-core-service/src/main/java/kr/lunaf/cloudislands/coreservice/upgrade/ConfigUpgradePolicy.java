package kr.lunaf.cloudislands.coreservice.upgrade;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;

public final class ConfigUpgradePolicy {
    private ConfigUpgradePolicy() {}

    public static UpgradePolicy load(String overrideFile) {
        String yaml = bundled();
        if (overrideFile != null && !overrideFile.isBlank()) {
            try {
                yaml = Files.readString(Path.of(overrideFile), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                return new UpgradePolicy();
            }
        }
        Map<String, UpgradeRule> rules = parse(yaml);
        return rules.isEmpty() ? new UpgradePolicy() : new UpgradePolicy(rules);
    }

    private static String bundled() {
        try (InputStream input = ConfigUpgradePolicy.class.getClassLoader().getResourceAsStream("rules/upgrades.yaml")) {
            return input == null ? "" : new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    private static Map<String, UpgradeRule> parse(String yaml) {
        Map<String, UpgradeRule> rules = new LinkedHashMap<>();
        String currentKey = "";
        UpgradeType currentType = null;
        int explicitMaxLevel = 0;
        BigDecimal explicitBaseCost = null;
        BigDecimal explicitMultiplier = null;
        Map<Integer, BigDecimal> levelCosts = new LinkedHashMap<>();
        Map<Integer, Long> levelValues = new LinkedHashMap<>();
        Map<Integer, Map<String, Long>> levelItemCosts = new LinkedHashMap<>();
        Map<Integer, Map<String, Long>> levelEffects = new LinkedHashMap<>();
        int currentLevel = 0;
        int currentLevelIndent = 0;
        boolean collectingItemCosts = false;
        String effectGroup = "";
        String effectSubgroup = "";
        for (String rawLine : yaml.split("\\R")) {
            String line = stripComment(rawLine);
            int indent = indentation(rawLine);
            if (line.isBlank() || !rawLine.startsWith("  ")) {
                continue;
            }
            if (indent == 2 && line.endsWith(":")) {
                if (!currentKey.isBlank()) {
                    putRule(rules, currentKey, currentType, explicitMaxLevel, explicitBaseCost, explicitMultiplier, levelCosts, levelValues, levelItemCosts, levelEffects);
                }
                currentKey = line.substring(0, line.length() - 1).trim();
                currentType = null;
                explicitMaxLevel = 0;
                explicitBaseCost = null;
                explicitMultiplier = null;
                levelCosts = new LinkedHashMap<>();
                levelValues = new LinkedHashMap<>();
                levelItemCosts = new LinkedHashMap<>();
                levelEffects = new LinkedHashMap<>();
                currentLevel = 0;
                currentLevelIndent = 0;
                collectingItemCosts = false;
                effectGroup = "";
                effectSubgroup = "";
                continue;
            }
            if (currentKey.isBlank()) {
                continue;
            }
            if (line.startsWith("type:")) {
                currentType = parseType(value(line));
            } else if (line.startsWith("max-level:") || line.startsWith("maxLevel:")) {
                explicitMaxLevel = integer(value(line), 0);
            } else if (line.startsWith("base-cost:") || line.startsWith("baseCost:")) {
                explicitBaseCost = decimal(value(line), null);
            } else if (line.startsWith("multiplier:")) {
                explicitMultiplier = decimal(value(line), null);
            } else if ((indent == 4 || indent == 6) && line.endsWith(":") && levelNumber(line) > 0) {
                currentLevel = levelNumber(line);
                currentLevelIndent = indent;
                collectingItemCosts = false;
                effectGroup = "";
                effectSubgroup = "";
            } else if (indent == currentLevelIndent + 2 && currentLevel > 0 && line.equals("item-costs:")) {
                collectingItemCosts = currentLevel > 0;
                effectGroup = "";
                effectSubgroup = "";
            } else if (indent == currentLevelIndent + 2 && currentLevel > 0 && line.endsWith(":")) {
                collectingItemCosts = false;
                effectGroup = effectKeyName(line.substring(0, line.length() - 1));
                effectSubgroup = "";
            } else if (indent == currentLevelIndent + 4 && collectingItemCosts && currentLevel > 0 && line.contains(":")) {
                int separator = line.lastIndexOf(':');
                String materialKey = line.substring(0, separator).trim();
                Long amount = longValue(line.substring(separator + 1).trim(), null);
                if (!materialKey.isBlank() && amount != null && amount > 0L) {
                    levelItemCosts.computeIfAbsent(currentLevel, ignored -> new LinkedHashMap<>()).put(materialKey, amount);
                }
            } else if (indent == currentLevelIndent + 4 && !effectGroup.isBlank() && currentLevel > 0 && line.endsWith(":")) {
                effectSubgroup = effectKeyName(line.substring(0, line.length() - 1));
            } else if (indent == currentLevelIndent + 4 && !effectGroup.isBlank() && currentLevel > 0 && line.contains(":")) {
                int separator = line.lastIndexOf(':');
                String nestedKey = line.substring(0, separator).trim();
                Long effectValue = longValue(line.substring(separator + 1).trim(), null);
                if (!nestedKey.isBlank() && effectValue != null && effectValue >= 0L) {
                    putEffect(levelEffects, currentLevel, effectGroup + "." + effectKeyName(nestedKey), effectValue);
                }
            } else if (indent == currentLevelIndent + 6 && !effectGroup.isBlank() && !effectSubgroup.isBlank() && currentLevel > 0 && line.contains(":")) {
                int separator = line.lastIndexOf(':');
                String nestedKey = line.substring(0, separator).trim();
                Long effectValue = longValue(line.substring(separator + 1).trim(), null);
                if (!nestedKey.isBlank() && effectValue != null && effectValue >= 0L) {
                    putEffect(levelEffects, currentLevel, effectGroup + "." + effectSubgroup + "." + effectKeyName(nestedKey), effectValue);
                }
            } else if (indent == currentLevelIndent + 2 && currentLevel > 0 && (line.startsWith("cost:") || line.startsWith("price:"))) {
                collectingItemCosts = false;
                effectGroup = "";
                effectSubgroup = "";
                BigDecimal cost = decimal(value(line), null);
                if (cost != null && cost.signum() >= 0) {
                    levelCosts.put(currentLevel, cost);
                }
            } else if (indent == currentLevelIndent + 2 && currentLevel > 0 && line.contains(":")) {
                collectingItemCosts = false;
                effectGroup = "";
                effectSubgroup = "";
                String configuredEffectKey = effectKeyName(line.substring(0, line.indexOf(':')));
                Long limitValue = configuredEffectValue(configuredEffectKey, value(line));
                if (limitValue != null && limitValue >= 0L && effectKey(line)) {
                    levelValues.putIfAbsent(currentLevel, limitValue);
                    putEffect(levelEffects, currentLevel, configuredEffectKey, limitValue);
                }
            }
        }
        if (!currentKey.isBlank()) {
            putRule(rules, currentKey, currentType, explicitMaxLevel, explicitBaseCost, explicitMultiplier, levelCosts, levelValues, levelItemCosts, levelEffects);
        }
        return rules;
    }

    private static void putRule(Map<String, UpgradeRule> rules, String key, UpgradeType type, int maxLevel, BigDecimal baseCost, BigDecimal multiplier, Map<Integer, BigDecimal> levelCosts, Map<Integer, Long> levelValues, Map<Integer, Map<String, Long>> levelItemCosts, Map<Integer, Map<String, Long>> levelEffects) {
        UpgradeType resolvedType = type == null ? inferredType(key, levelEffects) : type;
        Map<Integer, Long> resolvedLevelValues = primaryLevelValues(resolvedType, levelValues, levelEffects);
        int inferredMaxLevel = maxLevel > 0 ? maxLevel : Math.max(1, Math.max(levelEffects.keySet().stream().mapToInt(Integer::intValue).max().orElse(0), Math.max(levelItemCosts.keySet().stream().mapToInt(Integer::intValue).max().orElse(0), Math.max(levelCosts.keySet().stream().mapToInt(Integer::intValue).max().orElse(0), levelValues.keySet().stream().mapToInt(Integer::intValue).max().orElse(0)))));
        BigDecimal inferredBaseCost = baseCost != null && baseCost.signum() >= 0 ? baseCost : levelCosts.values().stream().filter(cost -> cost.signum() > 0).findFirst().orElse(BigDecimal.ZERO);
        BigDecimal inferredMultiplier = multiplier != null && multiplier.signum() > 0 ? multiplier : inferMultiplier(levelCosts, inferredBaseCost);
        rules.put(key.toLowerCase(), new UpgradeRule(key.toLowerCase(), resolvedType, inferredMaxLevel, inferredBaseCost, inferredMultiplier, levelCosts, resolvedLevelValues, levelItemCosts, levelEffects));
    }

    private static UpgradeType inferredType(String upgradeKey, Map<Integer, Map<String, Long>> effects) {
        boolean generatorRates = effects.values().stream()
            .flatMap(levelEffects -> levelEffects.keySet().stream())
            .anyMatch(effectKey -> effectKey.startsWith("generator-rates."));
        return generatorRates ? UpgradeType.GENERATOR_LEVEL : UpgradePolicy.typeFor(upgradeKey);
    }

    private static Map<Integer, Long> primaryLevelValues(UpgradeType type, Map<Integer, Long> fallback, Map<Integer, Map<String, Long>> effects) {
        java.util.List<String> keys = primaryEffectKeys(type);
        if (keys.isEmpty() || effects.isEmpty()) {
            return fallback;
        }
        Map<Integer, Long> selected = new LinkedHashMap<>();
        effects.forEach((level, levelEffects) -> keys.stream()
            .map(levelEffects::get)
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .ifPresent(value -> selected.put(level, value)));
        return selected.isEmpty() && effects.isEmpty() ? fallback : selected;
    }

    private static java.util.List<String> primaryEffectKeys(UpgradeType type) {
        if (type == null) {
            return java.util.List.of();
        }
        return switch (type) {
            case ISLAND_SIZE -> java.util.List.of("size", "island-size", "border-size");
            case MAX_MEMBERS, MEMBER_LIMIT -> java.util.List.of("team-limit", "members", "member-limit", "max-members");
            case MAX_WARPS, WARP_LIMIT -> java.util.List.of("warps-limit", "warps", "warp-limit");
            case HOME_LIMIT -> java.util.List.of("homes-limit", "homes", "home-limit", "max-homes");
            case BORDER_SIZE -> java.util.List.of("border-size", "size");
            case HOPPER_LIMIT -> java.util.List.of("hopper-limit", "hoppers-limit", "hoppers", "max-hoppers");
            case SPAWNER_LIMIT -> java.util.List.of("spawner-limit", "spawners-limit", "spawners", "max-spawners");
            case MOB_LIMIT -> java.util.List.of("mob-limit", "entity-limit", "entities-limit");
            case CROP_GROWTH -> java.util.List.of("crops-growth", "crop-growth");
            case REDSTONE_LIMIT -> java.util.List.of("redstone-limit", "redstone");
            case BANK_LIMIT -> java.util.List.of("bank-limit", "bank");
            case GENERATOR_LEVEL -> java.util.List.of("generator-level", "generator");
            case BIOME_UNLOCK, FLY_ACCESS, BORDER_COLOR_UNLOCK, KEEP_INVENTORY_ENABLE -> java.util.List.of();
        };
    }

    private static void putEffect(Map<Integer, Map<String, Long>> effects, int level, String key, long value) {
        effects.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(key, value);
    }

    private static String effectKeyName(String key) {
        return key.trim().toLowerCase().replace('_', '-');
    }

    private static int levelNumber(String line) {
        String candidate = line.substring(0, line.length() - 1).trim().replace("'", "").replace("\"", "");
        return integer(candidate, 0);
    }

    private static int indentation(String line) {
        int spaces = 0;
        while (spaces < line.length() && line.charAt(spaces) == ' ') {
            spaces++;
        }
        return spaces;
    }

    private static Long configuredEffectValue(String effectKey, String value) {
        if (effectKey.equals("crops-growth") || effectKey.equals("crop-growth") || effectKey.equals("mob-drops") || effectKey.equals("spawner-rates")) {
            BigDecimal multiplier = decimal(value, null);
            if (multiplier == null || multiplier.signum() < 0) {
                return null;
            }
            try {
                return multiplier.multiply(BigDecimal.valueOf(100L)).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
            } catch (ArithmeticException ignored) {
                return null;
            }
        }
        return longValue(value, null);
    }

    private static BigDecimal inferMultiplier(Map<Integer, BigDecimal> levelCosts, BigDecimal baseCost) {
        if (baseCost.signum() <= 0) {
            return BigDecimal.ONE;
        }
        return levelCosts.values().stream()
            .filter(cost -> cost.compareTo(baseCost) > 0)
            .findFirst()
            .map(cost -> cost.divide(baseCost, java.math.MathContext.DECIMAL64))
            .orElse(new BigDecimal("2"));
    }

    private static String stripComment(String line) {
        int comment = line.indexOf('#');
        return (comment >= 0 ? line.substring(0, comment) : line).trim();
    }

    private static String value(String line) {
        return line.substring(line.indexOf(':') + 1).trim().replace("\"", "");
    }

    private static UpgradeType parseType(String value) {
        try {
            return UpgradeType.valueOf(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static BigDecimal decimal(String value, BigDecimal fallback) {
        try {
            return new BigDecimal(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static Long longValue(String value, Long fallback) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean effectKey(String line) {
        String key = line.substring(0, line.indexOf(':')).trim().toLowerCase();
        return !key.equals("cost") && !key.equals("price");
    }
}
