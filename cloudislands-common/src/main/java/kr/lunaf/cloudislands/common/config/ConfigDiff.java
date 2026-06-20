package kr.lunaf.cloudislands.common.config;

import java.util.ArrayList;
import java.util.List;

public record ConfigDiff(List<String> changedLines, boolean restartRequired) {
    public ConfigDiff {
        changedLines = changedLines == null ? List.of() : List.copyOf(changedLines);
    }

    public static ConfigDiff between(String currentYaml, String candidateYaml, List<String> restartRequiredKeys) {
        List<String> changed = changedLines(currentYaml, candidateYaml);
        boolean requiresRestart = changed.stream().anyMatch(line -> restartRequired(line, restartRequiredKeys));
        return new ConfigDiff(changed, requiresRestart);
    }

    public boolean changed() {
        return !changedLines.isEmpty();
    }

    private static boolean restartRequired(String changedLine, List<String> restartRequiredKeys) {
        if (restartRequiredKeys == null || restartRequiredKeys.isEmpty()) {
            return false;
        }
        String path = changedLine;
        int separator = path.indexOf('=');
        if (separator > 0) {
            path = path.substring(0, separator);
        }
        String normalized = path.trim();
        return restartRequiredKeys.stream().anyMatch(key -> normalized.equals(key) || normalized.startsWith(key + "."));
    }

    private static List<String> changedLines(String currentYaml, String candidateYaml) {
        List<String> current = normalizedLines(currentYaml);
        List<String> candidate = normalizedLines(candidateYaml);
        int max = Math.max(current.size(), candidate.size());
        List<String> changed = new ArrayList<>();
        for (int index = 0; index < max; index++) {
            String left = index < current.size() ? current.get(index) : "";
            String right = index < candidate.size() ? candidate.get(index) : "";
            if (!left.equals(right) && !right.isBlank()) {
                changed.add(pathForLine(right));
            }
        }
        return List.copyOf(changed);
    }

    private static List<String> normalizedLines(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        List<Frame> stack = new ArrayList<>();
        for (String raw : yaml.split("\\R")) {
            String trimmed = raw.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("-")) {
                continue;
            }
            int separator = trimmed.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            int indent = leadingSpaces(raw);
            while (!stack.isEmpty() && stack.get(stack.size() - 1).indent() >= indent) {
                stack.remove(stack.size() - 1);
            }
            String key = trimmed.substring(0, separator).trim();
            String value = trimmed.substring(separator + 1).trim();
            String path = path(stack, key);
            if (value.isBlank()) {
                stack.add(new Frame(indent, key));
            } else {
                lines.add(path + "=" + ConfigV2Validator.cleanScalar(value));
            }
        }
        return lines;
    }

    private static String pathForLine(String line) {
        int separator = line.indexOf('=');
        return separator < 0 ? line : line.substring(0, separator);
    }

    private static String path(List<Frame> stack, String key) {
        String parent = stack.stream().map(Frame::key).reduce((left, right) -> left + "." + right).orElse("");
        return parent.isBlank() ? key : parent + "." + key;
    }

    private static int leadingSpaces(String value) {
        int count = 0;
        while (count < value.length() && value.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private record Frame(int indent, String key) {
    }
}
