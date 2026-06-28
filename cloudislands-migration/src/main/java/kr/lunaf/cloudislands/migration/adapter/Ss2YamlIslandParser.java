package kr.lunaf.cloudislands.migration.adapter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

public final class Ss2YamlIslandParser implements Ss2IslandDocumentParser {
    @Override
    public ParsedIslandDocument parse(String content) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        LinkedHashMap<String, List<String>> lists = new LinkedHashMap<>();
        Deque<YamlNode> stack = new ArrayDeque<>();
        String[] lines = (content == null ? "" : content).split("\\R");
        for (String rawLine : lines) {
            String uncommented = stripYamlComment(rawLine);
            String trimmed = uncommented.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int indent = leadingSpaces(uncommented);
            while (!stack.isEmpty() && stack.peek().indent() >= indent) {
                stack.pop();
            }
            String parent = stack.isEmpty() ? "" : stack.peek().path();
            if (trimmed.startsWith("-")) {
                String item = cleanScalar(trimmed.substring(1).trim());
                if (!parent.isBlank() && !item.isBlank()) {
                    lists.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(item);
                }
                continue;
            }
            String key = yamlKey(trimmed);
            if (key.isBlank()) {
                continue;
            }
            String path = parent.isBlank() ? key : parent + "." + key;
            String value = yamlValue(trimmed);
            if (value.isBlank()) {
                stack.push(new YamlNode(indent, path));
                continue;
            }
            if (value.startsWith("[") && value.endsWith("]")) {
                LinkedHashSet<String> collected = new LinkedHashSet<>();
                collectStringValues(value, collected);
                if (!collected.isEmpty()) {
                    lists.put(path, List.copyOf(collected));
                }
                continue;
            }
            values.put(path, value);
        }
        lists.replaceAll((key, value) -> List.copyOf(value));
        return new ParsedIslandDocument(values, lists);
    }

    private String yamlKey(String trimmedLine) {
        String line = trimmedLine;
        if (line.startsWith("-")) {
            line = line.substring(1).trim();
        }
        int colon = yamlSeparator(line);
        if (colon < 0) {
            return "";
        }
        String key = line.substring(0, colon).trim();
        if ((key.startsWith("\"") && key.endsWith("\"")) || (key.startsWith("'") && key.endsWith("'"))) {
            key = key.substring(1, key.length() - 1);
        }
        return key.trim();
    }

    private String yamlValue(String trimmedLine) {
        String line = trimmedLine;
        if (line.startsWith("-")) {
            line = line.substring(1).trim();
        }
        int colon = yamlSeparator(line);
        if (colon < 0) {
            return "";
        }
        return cleanScalar(line.substring(colon + 1).trim());
    }

    private int yamlSeparator(String line) {
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
            } else if (current == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
            } else if (current == ':' && !singleQuoted && !doubleQuoted) {
                return index;
            }
        }
        return -1;
    }

    private String cleanScalar(String value) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
            return cleaned;
        }
        boolean quoted = (cleaned.startsWith("\"") && cleaned.endsWith("\"")) || (cleaned.startsWith("'") && cleaned.endsWith("'"));
        if (quoted) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        int comment = quoted ? -1 : cleaned.indexOf(" #");
        if (comment >= 0) {
            cleaned = cleaned.substring(0, comment).trim();
        }
        return cleaned;
    }

    private void collectStringValues(String text, LinkedHashSet<String> values) {
        String normalized = text.replace("[", "").replace("]", "");
        for (String token : normalized.split(",")) {
            String value = token.trim();
            if (value.startsWith("-")) {
                value = value.substring(1).trim();
            }
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            if (!value.isBlank()) {
                values.add(value.toLowerCase());
            }
        }
    }

    private String stripYamlComment(String line) {
        if (line == null || line.isBlank()) {
            return "";
        }
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
            } else if (current == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
            } else if (current == '#' && !singleQuoted && !doubleQuoted && (index == 0 || Character.isWhitespace(line.charAt(index - 1)))) {
                return line.substring(0, index);
            }
        }
        return line;
    }

    private int leadingSpaces(String line) {
        int index = 0;
        while (line != null && index < line.length() && line.charAt(index) == ' ') {
            index++;
        }
        return index;
    }

    private record YamlNode(int indent, String path) {}
}
