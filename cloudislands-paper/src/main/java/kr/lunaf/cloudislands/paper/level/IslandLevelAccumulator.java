package kr.lunaf.cloudislands.paper.level;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public final class IslandLevelAccumulator {
    private final Map<String, Long> blockCounts = new HashMap<>();

    public void placed(String materialKey) {
        blockCounts.merge(materialKey, 1L, Long::sum);
    }

    public void broken(String materialKey) {
        blockCounts.computeIfPresent(materialKey, (key, value) -> Math.max(0L, value - 1L));
    }

    public BigDecimal worth(Map<String, BlockValueRule> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, Long> entry : blockCounts.entrySet()) {
            BlockValueRule rule = values.get(entry.getKey());
            if (rule != null) {
                long counted = rule.limit() <= 0 ? entry.getValue() : Math.min(entry.getValue(), rule.limit());
                total = total.add(rule.worth().multiply(BigDecimal.valueOf(counted)));
            }
        }
        return total;
    }

    public long levelPoints(Map<String, BlockValueRule> values) {
        long total = 0L;
        for (Map.Entry<String, Long> entry : blockCounts.entrySet()) {
            BlockValueRule rule = values.get(entry.getKey());
            if (rule != null) {
                long counted = rule.limit() <= 0 ? entry.getValue() : Math.min(entry.getValue(), rule.limit());
                total += rule.levelPoints() * counted;
            }
        }
        return total;
    }
}
