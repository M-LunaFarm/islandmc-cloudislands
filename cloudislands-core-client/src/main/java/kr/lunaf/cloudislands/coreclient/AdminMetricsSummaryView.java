package kr.lunaf.cloudislands.coreclient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record AdminMetricsSummaryView(long samples, List<String> names, Map<String, Double> latestValues) {
    public AdminMetricsSummaryView(long samples, List<String> names) {
        this(samples, names, Map.of());
    }

    public AdminMetricsSummaryView {
        names = names == null ? List.of() : List.copyOf(names);
        latestValues = latestValues == null ? Map.of() : Map.copyOf(latestValues);
    }

    public double value(String metricName) {
        if (metricName == null || metricName.isBlank()) {
            return 0.0D;
        }
        return latestValues.getOrDefault(metricName, 0.0D);
    }

    public boolean hasValue(String metricName) {
        return metricName != null && latestValues.containsKey(metricName);
    }

    static AdminMetricsSummaryView parse(String body) {
        if (body == null || body.isBlank()) {
            return new AdminMetricsSummaryView(0L, List.of(), Map.of());
        }
        long samples = 0L;
        List<String> names = new ArrayList<>();
        Map<String, Double> latestValues = new LinkedHashMap<>();
        Set<String> unlabeledFamilies = new java.util.LinkedHashSet<>();
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }
            samples++;
            int brace = trimmed.indexOf('{');
            int space = trimmed.indexOf(' ');
            int end = brace > 0 ? brace : space > 0 ? space : trimmed.length();
            String name = trimmed.substring(0, end);
            if (names.size() < 6) {
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
            Double value = sampleValue(trimmed, space);
            if (value != null) {
                boolean unlabeled = brace < 0;
                if (unlabeled) {
                    latestValues.put(name, value);
                    unlabeledFamilies.add(name);
                } else if (!unlabeledFamilies.contains(name)) {
                    latestValues.merge(name, value, Double::sum);
                }
            }
        }
        return new AdminMetricsSummaryView(samples, names, latestValues);
    }

    private static Double sampleValue(String line, int firstSpace) {
        if (firstSpace < 0 || firstSpace + 1 >= line.length()) {
            return null;
        }
        String valuePart = line.substring(firstSpace + 1).trim();
        if (valuePart.isBlank()) {
            return null;
        }
        int end = valuePart.indexOf(' ');
        String rawValue = end >= 0 ? valuePart.substring(0, end) : valuePart;
        try {
            return Double.parseDouble(rawValue);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
