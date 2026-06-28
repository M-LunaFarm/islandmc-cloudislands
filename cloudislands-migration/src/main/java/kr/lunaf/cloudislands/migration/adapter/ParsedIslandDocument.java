package kr.lunaf.cloudislands.migration.adapter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ParsedIslandDocument(Map<String, String> values, Map<String, List<String>> lists) {
    public String value(String key) {
        return values.get(key);
    }

    public boolean hasKey(String key) {
        return values.containsKey(key) || lists.containsKey(key);
    }

    public List<String> list(String key) {
        return lists.getOrDefault(key, List.of());
    }

    public Set<String> keys() {
        LinkedHashSet<String> keys = new LinkedHashSet<>(values.keySet());
        keys.addAll(lists.keySet());
        return keys;
    }
}
