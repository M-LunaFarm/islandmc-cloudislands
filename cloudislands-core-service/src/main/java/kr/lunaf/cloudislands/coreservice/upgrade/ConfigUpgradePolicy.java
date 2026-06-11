package kr.lunaf.cloudislands.coreservice.upgrade;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        List<BigDecimal> levelCosts = new ArrayList<>();
        for (String rawLine : yaml.split("\\R")) {
            String line = stripComment(rawLine);
            if (line.isBlank() || !rawLine.startsWith("  ")) {
                continue;
            }
            if (rawLine.startsWith("  ") && !rawLine.startsWith("    ") && line.endsWith(":")) {
                if (!currentKey.isBlank()) {
                    putRule(rules, currentKey, currentType, explicitMaxLevel, explicitBaseCost, explicitMultiplier, levelCosts);
                }
                currentKey = line.substring(0, line.length() - 1).trim();
                currentType = null;
                explicitMaxLevel = 0;
                explicitBaseCost = null;
                explicitMultiplier = null;
                levelCosts = new ArrayList<>();
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
            } else if (rawLine.startsWith("        ") && line.startsWith("cost:")) {
                BigDecimal cost = decimal(value(line), null);
                if (cost != null) {
                    levelCosts.add(cost);
                }
            }
        }
        if (!currentKey.isBlank()) {
            putRule(rules, currentKey, currentType, explicitMaxLevel, explicitBaseCost, explicitMultiplier, levelCosts);
        }
        return rules;
    }

    private static void putRule(Map<String, UpgradeRule> rules, String key, UpgradeType type, int maxLevel, BigDecimal baseCost, BigDecimal multiplier, List<BigDecimal> levelCosts) {
        int inferredMaxLevel = maxLevel > 0 ? maxLevel : Math.max(1, levelCosts.size());
        BigDecimal inferredBaseCost = baseCost != null ? baseCost : levelCosts.stream().filter(cost -> cost.signum() > 0).findFirst().orElse(BigDecimal.ZERO);
        BigDecimal inferredMultiplier = multiplier != null ? multiplier : inferMultiplier(levelCosts, inferredBaseCost);
        rules.put(key.toLowerCase(), new UpgradeRule(key.toLowerCase(), type == null ? UpgradePolicy.typeFor(key) : type, inferredMaxLevel, inferredBaseCost, inferredMultiplier));
    }

    private static BigDecimal inferMultiplier(List<BigDecimal> levelCosts, BigDecimal baseCost) {
        if (baseCost.signum() <= 0) {
            return BigDecimal.ONE;
        }
        return levelCosts.stream()
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
}
