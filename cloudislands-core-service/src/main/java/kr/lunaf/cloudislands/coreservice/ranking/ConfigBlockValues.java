package kr.lunaf.cloudislands.coreservice.ranking;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigBlockValues {
    private ConfigBlockValues() {}

    public static Map<String, RankingRecalculationService.BlockValue> load(String overrideFile) {
        String yaml = bundled();
        if (overrideFile != null && !overrideFile.isBlank()) {
            try {
                yaml = Files.readString(Path.of(overrideFile), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                return Map.of();
            }
        }
        return parse(yaml);
    }

    private static String bundled() {
        try (InputStream input = ConfigBlockValues.class.getClassLoader().getResourceAsStream("rules/block-values.yaml")) {
            return input == null ? "" : new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    private static Map<String, RankingRecalculationService.BlockValue> parse(String yaml) {
        Map<String, RankingRecalculationService.BlockValue> values = new LinkedHashMap<>();
        String currentKey = "";
        BigDecimal worth = BigDecimal.ZERO;
        long levelPoints = 0L;
        long limit = 0L;
        for (String rawLine : yaml.split("\\R")) {
            String line = stripComment(rawLine);
            if (line.isBlank()) {
                continue;
            }
            if (rawLine.startsWith("  ") && !rawLine.startsWith("    ") && line.endsWith(":")) {
                if (!currentKey.isBlank()) {
                    values.put(currentKey, new RankingRecalculationService.BlockValue(worth, levelPoints, limit));
                }
                currentKey = line.substring(0, line.length() - 1).trim();
                worth = BigDecimal.ZERO;
                levelPoints = 0L;
                limit = 0L;
                continue;
            }
            if (currentKey.isBlank() || !rawLine.startsWith("    ")) {
                continue;
            }
            if (line.startsWith("worth:")) {
                worth = decimal(value(line), BigDecimal.ZERO);
            } else if (line.startsWith("level:") || line.startsWith("level-points:") || line.startsWith("levelPoints:")) {
                levelPoints = number(value(line), 0L);
            } else if (line.startsWith("limit:")) {
                limit = number(value(line), 0L);
            }
        }
        if (!currentKey.isBlank()) {
            values.put(currentKey, new RankingRecalculationService.BlockValue(worth, levelPoints, limit));
        }
        return values;
    }

    private static String stripComment(String line) {
        int comment = line.indexOf('#');
        return (comment >= 0 ? line.substring(0, comment) : line).trim();
    }

    private static String value(String line) {
        return line.substring(line.indexOf(':') + 1).trim().replace("\"", "");
    }

    private static BigDecimal decimal(String value, BigDecimal fallback) {
        try {
            return new BigDecimal(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long number(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
