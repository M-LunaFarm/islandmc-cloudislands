package kr.lunaf.cloudislands.migration.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.json.JsonCodec;

public final class Ss2JsonIslandParser implements Ss2IslandDocumentParser {
    @Override
    public ParsedIslandDocument parse(String content) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        LinkedHashMap<String, List<String>> lists = new LinkedHashMap<>();
        flatten("", JsonCodec.readObject(content == null ? "" : content), values, lists);
        return new ParsedIslandDocument(
            Collections.unmodifiableMap(new LinkedHashMap<>(values)),
            Collections.unmodifiableMap(new LinkedHashMap<>(lists))
        );
    }

    private void flatten(String prefix, Object value, Map<String, String> values, Map<String, List<String>> lists) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().toString();
                if (!key.isBlank()) {
                    flatten(prefix.isBlank() ? key : prefix + "." + key, entry.getValue(), values, lists);
                }
            }
            return;
        }
        if (value instanceof List<?> list) {
            ArrayList<String> scalars = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> || item instanceof List<?>) {
                    continue;
                }
                if (item != null) {
                    scalars.add(item.toString());
                }
            }
            if (!prefix.isBlank() && !scalars.isEmpty()) {
                lists.put(prefix, List.copyOf(scalars));
            }
            return;
        }
        if (!prefix.isBlank() && value != null) {
            values.put(prefix, value.toString());
        }
    }
}
