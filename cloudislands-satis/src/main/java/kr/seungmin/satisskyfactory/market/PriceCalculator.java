package kr.seungmin.satisskyfactory.market;

import kr.seungmin.satisskyfactory.item.ItemRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class PriceCalculator {
    public record Factors(double serverDemandFactor, double personalFactor, double qualityFactor) {
    }

    public record PersonalTier(long amount, double factor) {
    }

    private final ItemRegistry items;
    private final Map<String, Long> prices;
    private final Map<String, Long> targetDailyAmounts;
    private final Map<String, Double> itemQualityFactors;
    private final Map<String, Double> tagQualityFactors;
    private final List<PersonalTier> personalTiers;
    private final boolean personalSoftCapEnabled;
    private final int personalSoftCap;
    private final double demandFloor;
    private final double demandCeiling;
    private final double demandExponent;

    public PriceCalculator(ItemRegistry items, Map<String, Long> prices, Map<String, Long> targetDailyAmounts,
                           Map<String, Double> itemQualityFactors, Map<String, Double> tagQualityFactors,
                           List<PersonalTier> personalTiers, boolean personalSoftCapEnabled, int personalSoftCap,
                           double demandFloor, double demandCeiling, double demandExponent) {
        this.items = items;
        this.prices = Map.copyOf(prices);
        this.targetDailyAmounts = Map.copyOf(targetDailyAmounts);
        this.itemQualityFactors = Map.copyOf(itemQualityFactors);
        this.tagQualityFactors = Map.copyOf(tagQualityFactors);
        this.personalTiers = personalTiers.stream()
                .sorted(Comparator.comparingLong(PersonalTier::amount))
                .toList();
        this.personalSoftCapEnabled = personalSoftCapEnabled;
        this.personalSoftCap = personalSoftCap;
        this.demandFloor = demandFloor;
        this.demandCeiling = demandCeiling;
        this.demandExponent = demandExponent;
    }

    public long basePrice(String itemId, long amount) {
        return boundedPrice(BigDecimal.valueOf(prices.getOrDefault(itemId, 0L))
                .multiply(BigDecimal.valueOf(amount)));
    }

    public long finalPrice(String itemId, long amount, long serverSoldWithAmount, long personalSoldWithAmount) {
        Factors factors = factors(itemId, amount, serverSoldWithAmount, personalSoldWithAmount);
        BigDecimal value = BigDecimal.valueOf(prices.getOrDefault(itemId, 0L))
                .multiply(BigDecimal.valueOf(amount))
                .multiply(BigDecimal.valueOf(safeFactor(factors.serverDemandFactor())))
                .multiply(BigDecimal.valueOf(safeFactor(factors.personalFactor())))
                .multiply(BigDecimal.valueOf(safeFactor(factors.qualityFactor())));
        return boundedPrice(value);
    }

    public Factors factors(String itemId, long amount, long serverSoldWithAmount, long personalSoldWithAmount) {
        double target = Math.max(1.0, targetDailyAmounts.getOrDefault(itemId, Math.max(1, personalSoftCap * 4L)));
        double serverFactor = Math.pow(target / Math.max(1.0, serverSoldWithAmount), demandExponent);
        serverFactor = clamp(serverFactor, demandFloor, demandCeiling);
        return new Factors(serverFactor, personalFactor(personalSoldWithAmount), qualityFactor(itemId));
    }

    private double qualityFactor(String itemId) {
        Double itemFactor = itemQualityFactors.get(itemId);
        if (itemFactor != null) {
            return itemFactor;
        }
        return items.get(itemId)
                .filter(kr.seungmin.satisskyfactory.item.ItemDefinition::qualityEnabled)
                .map(item -> item.tags().stream()
                        .map(tag -> tagQualityFactors.getOrDefault(tag.toLowerCase(), 1.0))
                        .max(Double::compareTo)
                        .orElse(1.0))
                .orElse(1.0);
    }

    private double personalFactor(long totalSold) {
        if (!personalSoftCapEnabled) {
            return 1.0;
        }
        if (!personalTiers.isEmpty()) {
            return personalTiers.stream()
                    .filter(tier -> totalSold <= tier.amount())
                    .findFirst()
                    .map(PersonalTier::factor)
                    .orElseGet(() -> personalTiers.get(personalTiers.size() - 1).factor());
        }
        return totalSold > personalSoftCap
                ? Math.max(demandFloor, (double) personalSoftCap / totalSold)
                : 1.0;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long boundedPrice(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return 0L;
        }
        BigDecimal rounded = value.setScale(0, RoundingMode.HALF_UP);
        return rounded.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) >= 0
                ? Long.MAX_VALUE
                : rounded.longValue();
    }

    private static double safeFactor(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }
}
