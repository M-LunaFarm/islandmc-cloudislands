package kr.seungmin.satisskyfactory.market;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.item.ItemDefinition;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MaintenanceStatus;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import kr.seungmin.satisskyfactory.util.TimeUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class MarketService {
    public record SellResult(long gross, long paidToPlayer, long debtRepaid, double serverDemandFactor,
                             double personalFactor, double qualityFactor) {
    }

    private final StorageService storage;
    private final EconomyService economy;
    private final DatabaseService database;
    private final ItemRegistry items;
    private final BooleanSupplier maintenanceEnabled;
    private final Predicate<FactoryIsland> islandSaver;
    private BooleanSupplier writesEnabled = () -> true;
    private final Map<String, Long> prices = new HashMap<>();
    private final Map<String, Long> targetDailyAmounts = new HashMap<>();
    private final Map<String, Double> itemQualityFactors = new HashMap<>();
    private final Map<String, Double> tagQualityFactors = new HashMap<>();
    private final List<PriceCalculator.PersonalTier> personalTiers = new ArrayList<>();
    private boolean personalSoftCapEnabled = true;
    private int personalSoftCap = 256;
    private double demandFloor = 0.35;
    private double demandCeiling = 1.25;
    private double demandExponent = 0.35;
    private double debtRepayRate = 0.35;
    private double lockedDebtRepayRate = 0.70;
    private boolean lockedMarketSalesBlocked;
    private boolean active;

    public MarketService(StorageService storage, EconomyService economy, DatabaseService database, ItemRegistry items) {
        this(storage, economy, database, items, () -> true);
    }

    public MarketService(StorageService storage, EconomyService economy, DatabaseService database, ItemRegistry items,
                         BooleanSupplier maintenanceEnabled) {
        this(storage, economy, database, items, maintenanceEnabled, island -> {
            database.saveIsland(island);
            return true;
        });
    }

    public MarketService(StorageService storage, EconomyService economy, DatabaseService database, ItemRegistry items,
                         BooleanSupplier maintenanceEnabled, Predicate<FactoryIsland> islandSaver) {
        this.storage = storage;
        this.economy = economy;
        this.database = database;
        this.items = items;
        this.maintenanceEnabled = maintenanceEnabled == null ? () -> true : maintenanceEnabled;
        this.islandSaver = islandSaver == null ? island -> {
            database.saveIsland(island);
            return true;
        } : islandSaver;
    }

    public void writeGate(BooleanSupplier writesEnabled) {
        this.writesEnabled = writesEnabled == null ? () -> true : writesEnabled;
    }

    public void load(FileConfiguration config) {
        load(config, null);
    }

    public void load(FileConfiguration config, FileConfiguration maintenanceConfig) {
        clear();
        active = true;
        personalSoftCapEnabled = config.getBoolean("market.personal-soft-cap.enabled", true);
        personalSoftCap = config.isInt("market.personal-soft-cap") ? config.getInt("market.personal-soft-cap", 256) : 256;
        demandFloor = config.getDouble("market.factor-min", config.getDouble("market.demand-floor", 0.35));
        demandCeiling = config.getDouble("market.factor-max", config.getDouble("market.demand-ceiling", 1.25));
        demandExponent = config.getDouble("market.demand-exponent", 0.35);
        debtRepayRate = config.getDouble("market.debt-repay-rate", 0.35);
        lockedDebtRepayRate = config.getDouble("market.locked-debt-repay-rate", 0.70);
        if (maintenanceConfig != null) {
            lockedMarketSalesBlocked = maintenanceConfig.getBoolean("maintenance.locked.block-market-sales", false);
            if (maintenanceConfig.contains("maintenance.locked.auto-pay-debt-from-sales-percent")) {
                lockedDebtRepayRate = maintenanceConfig.getDouble("maintenance.locked.auto-pay-debt-from-sales-percent", 70.0) / 100.0;
            }
        }
        loadPersonalTiers(config);
        loadQualityFactors(config);
        ConfigurationSection marketItems = config.getConfigurationSection("market.items");
        if (marketItems == null) {
            return;
        }
        for (String itemId : marketItems.getKeys(false)) {
            if (items.isVirtualOnly(itemId)) {
                continue;
            }
            prices.put(itemId, marketItems.contains(itemId + ".base-price")
                    ? marketItems.getLong(itemId + ".base-price", 1)
                    : itemBasePrice(itemId));
            targetDailyAmounts.put(itemId, marketItems.getLong(itemId + ".target-daily-amount", Math.max(1, personalSoftCap * 4L)));
            if (marketItems.contains(itemId + ".quality-factor")) {
                itemQualityFactors.put(itemId, Math.max(0.0, marketItems.getDouble(itemId + ".quality-factor", 1.0)));
            }
        }
    }

    public void clear() {
        active = false;
        prices.clear();
        targetDailyAmounts.clear();
        itemQualityFactors.clear();
        tagQualityFactors.clear();
        personalTiers.clear();
        lockedMarketSalesBlocked = false;
        personalSoftCapEnabled = true;
        personalSoftCap = 256;
        demandFloor = 0.35;
        demandCeiling = 1.25;
        demandExponent = 0.35;
        debtRepayRate = 0.35;
        lockedDebtRepayRate = 0.70;
    }

    public long price(String itemId, long amount) {
        if (!active) {
            return 0L;
        }
        return calculator().basePrice(itemId, amount);
    }

    public long price(UUID islandUuid, String itemId, long amount) {
        if (!active) {
            return 0L;
        }
        String dateKey = dateKey();
        return calculator().finalPrice(
                itemId,
                amount,
                saturatingNonNegativeAdd(database.marketDailySold(itemId, dateKey), amount),
                saturatingNonNegativeAdd(database.marketPersonalSold(islandUuid, itemId, dateKey), amount)
        );
    }

    public Optional<SellResult> sell(FactoryIsland island, OfflinePlayer owner, String itemId, long amount) {
        if (!active) {
            return Optional.empty();
        }
        if (!writesEnabled()) {
            return Optional.empty();
        }
        if (amount <= 0 || !prices.containsKey(itemId) || marketBlocked(island)) {
            return Optional.empty();
        }
        VirtualInventory inventory = storage.islandStorageIfAllowed(island.islandUuid()).orElse(null);
        if (inventory == null) {
            return Optional.empty();
        }
        if (!inventory.remove(itemId, amount)) {
            return Optional.empty();
        }
        if (!storage.saveIfAllowed(inventory)) {
            inventory.add(itemId, amount);
            return Optional.empty();
        }
        Optional<SellResult> result;
        try {
            result = payout(island, owner, "MARKET_SELL", itemId, amount);
        } catch (RuntimeException payoutFailure) {
            result = Optional.empty();
        }
        if (result.isEmpty()) {
            if (!restoreSoldInventory(inventory, itemId, amount)) {
                inventory.remove(itemId, amount);
            }
            return Optional.empty();
        }
        return result;
    }

    private boolean restoreSoldInventory(VirtualInventory inventory, String itemId, long amount) {
        inventory.add(itemId, amount);
        return storage.saveIfAllowed(inventory);
    }

    public Optional<SellResult> sellDirect(FactoryIsland island, OfflinePlayer owner, String itemId, long amount) {
        if (!active) {
            return Optional.empty();
        }
        if (!writesEnabled()) {
            return Optional.empty();
        }
        if (amount <= 0 || !prices.containsKey(itemId) || marketBlocked(island)) {
            return Optional.empty();
        }
        try {
            return payout(island, owner, "MARKET_SELL_HAND", itemId, amount);
        } catch (RuntimeException payoutFailure) {
            return Optional.empty();
        }
    }

    public Map<String, Long> prices() {
        if (!active) {
            return Map.of();
        }
        return Map.copyOf(prices);
    }

    private long itemBasePrice(String itemId) {
        return items.get(itemId)
                .map(ItemDefinition::basePrice)
                .filter(price -> price > 0)
                .orElse(1L);
    }

    private boolean marketBlocked(FactoryIsland island) {
        return maintenanceEnabled() && lockedMarketSalesBlocked && island.maintenanceStatus() == MaintenanceStatus.LOCKED;
    }

    private boolean writesEnabled() {
        try {
            return writesEnabled.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean maintenanceEnabled() {
        try {
            return maintenanceEnabled.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private Optional<SellResult> payout(FactoryIsland island, OfflinePlayer owner, String operation, String itemId, long amount) {
        String dateKey = dateKey();
        long previousServerSold = database.marketDailySold(itemId, dateKey);
        long previousPersonalSold = database.marketPersonalSold(island.islandUuid(), itemId, dateKey);
        long serverSold = saturatingNonNegativeAdd(previousServerSold, amount);
        long personalSold = saturatingNonNegativeAdd(previousPersonalSold, amount);
        PriceCalculator.Factors factors = calculator().factors(itemId, amount, serverSold, personalSold);
        long gross = calculator().finalPrice(itemId, amount, serverSold, personalSold);
        long debtRepaid = 0;
        long previousDebt = island.maintenanceDebt();
        if (maintenanceEnabled() && island.maintenanceDebt() > 0) {
            double repayRate = island.maintenanceStatus() == MaintenanceStatus.LOCKED ? lockedDebtRepayRate : debtRepayRate;
            debtRepaid = Math.min(island.maintenanceDebt(), Math.round(gross * clamp(repayRate, 0.0, 1.0)));
        }
        long paid = Math.max(0, gross - debtRepaid);
        String reason = itemId + " x" + amount;
        String idempotencyKey = marketIdempotencyKey(operation, island, owner, itemId, amount, dateKey,
                previousServerSold, previousPersonalSold);
        DatabaseService.EconomyLedgerClaim claim = database.beginEconomyLedger(
                island.islandUuid(), playerUuid(owner), operation, gross, reason, idempotencyKey);
        if (claim != DatabaseService.EconomyLedgerClaim.STARTED) {
            return Optional.empty();
        }
        if (debtRepaid > 0) {
            island.maintenanceDebt(island.maintenanceDebt() - debtRepaid);
            if (!islandSaver.test(island)) {
                island.maintenanceDebt(previousDebt);
                database.failEconomyLedger(idempotencyKey);
                return Optional.empty();
            }
        }
        if (paid > 0 && !economy.deposit(owner, paid)) {
            if (debtRepaid > 0) {
                island.maintenanceDebt(previousDebt);
                islandSaver.test(island);
            }
            database.failEconomyLedger(idempotencyKey);
            return Optional.empty();
        }
        try {
            database.recordMarketSale(island.islandUuid(), itemId, dateKey, amount, factors.serverDemandFactor());
            database.addLedger(island.islandUuid(), operation, gross, reason);
            if (debtRepaid > 0) {
                database.addLedger(island.islandUuid(), "MARKET_DEBT_REPAY", debtRepaid, reason);
            }
            database.completeEconomyLedger(idempotencyKey);
        } catch (RuntimeException exception) {
            if (paid > 0) {
                try {
                    economy.withdraw(owner, paid);
                } catch (RuntimeException ignored) {
                    // The durable compensation state below remains authoritative for operators.
                }
            }
            if (debtRepaid > 0) {
                island.maintenanceDebt(previousDebt);
                try {
                    islandSaver.test(island);
                } catch (RuntimeException ignored) {
                    // The previous debt remains in memory and the ledger records reconciliation work.
                }
            }
            try {
                database.compensateEconomyLedger(idempotencyKey);
            } catch (RuntimeException ignored) {
                // A database outage must not prevent the caller from restoring sold inventory.
            }
            return Optional.empty();
        }
        return Optional.of(new SellResult(gross, paid, debtRepaid, factors.serverDemandFactor(), factors.personalFactor(), factors.qualityFactor()));
    }

    private String marketIdempotencyKey(String operation, FactoryIsland island, OfflinePlayer owner, String itemId, long amount,
                                        String dateKey, long previousServerSold, long previousPersonalSold) {
        return String.join(":",
                operation,
                island.islandUuid().toString(),
                playerUuid(owner) == null ? "system" : playerUuid(owner).toString(),
                itemId,
                Long.toString(amount),
                dateKey,
                Long.toString(previousServerSold),
                Long.toString(previousPersonalSold),
                Long.toString(island.maintenanceDebt()));
    }

    private UUID playerUuid(OfflinePlayer owner) {
        return owner == null ? null : owner.getUniqueId();
    }

    private void loadPersonalTiers(FileConfiguration config) {
        for (Map<?, ?> tier : config.getMapList("market.personal-soft-cap.tiers")) {
            Object amountValue = tier.get("amount");
            Object factorValue = tier.get("factor");
            if (amountValue == null || factorValue == null) {
                continue;
            }
            long amount = asLong(amountValue, 0);
            double factor = asDouble(factorValue, 1.0);
            if (amount > 0 && factor > 0) {
                personalTiers.add(new PriceCalculator.PersonalTier(amount, factor));
            }
        }
        personalTiers.sort(Comparator.comparingLong(PriceCalculator.PersonalTier::amount));
    }

    private void loadQualityFactors(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("market.quality-factor.tags");
        if (section == null) {
            tagQualityFactors.put("quality", 1.15);
            return;
        }
        for (String tag : section.getKeys(false)) {
            tagQualityFactors.put(tag.toLowerCase(), Math.max(0.0, section.getDouble(tag, 1.0)));
        }
    }

    private long asLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private double asDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String dateKey() {
        return TimeUtil.todayKey();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long saturatingNonNegativeAdd(long current, long amount) {
        try {
            return Math.max(0L, Math.addExact(current, amount));
        } catch (ArithmeticException overflow) {
            return amount > 0L ? Long.MAX_VALUE : 0L;
        }
    }

    private PriceCalculator calculator() {
        return new PriceCalculator(
                items,
                prices,
                targetDailyAmounts,
                itemQualityFactors,
                tagQualityFactors,
                personalTiers,
                personalSoftCapEnabled,
                personalSoftCap,
                demandFloor,
                demandCeiling,
                demandExponent
        );
    }
}
