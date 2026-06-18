package kr.seungmin.satisskyfactory.gui;

import java.util.Optional;
import java.util.function.Predicate;

public final class FactoryGuiFeaturePolicy {
    private FactoryGuiFeaturePolicy() {
    }

    public static boolean canHandle(String actionType, Predicate<String> featureEnabled) {
        return blockedFeature(actionType, featureEnabled).isEmpty();
    }

    public static Optional<String> blockedFeature(String actionType, Predicate<String> featureEnabled) {
        if (!enabled(featureEnabled, "gui")) {
            return Optional.of("gui");
        }
        Optional<String> primary = primaryFeature(actionType);
        if (primary.isPresent() && !enabled(featureEnabled, primary.get())) {
            return primary;
        }
        Optional<String> secondary = secondaryFeature(actionType);
        if (secondary.isPresent() && !enabled(featureEnabled, secondary.get())) {
            return secondary;
        }
        Optional<String> tertiary = tertiaryFeature(actionType);
        if (tertiary.isPresent() && !enabled(featureEnabled, tertiary.get())) {
            return tertiary;
        }
        return Optional.empty();
    }

    private static Optional<String> primaryFeature(String actionType) {
        return switch (actionType) {
            case "market_page", "main_market", "sell_market_item" -> Optional.of("market");
            case "storage_page", "main_storage", "withdraw_storage", "deposit_hand" -> Optional.of("storage");
            case "main_contracts", "contracts_back", "contract_detail", "complete_contract", "complete_emergency" -> Optional.of("contracts");
            case "main_research", "unlock_research" -> Optional.of("research");
            case "deposit_machine_input", "withdraw_machine_input", "withdraw_machine_output", "select_recipe", "reclaim_machine" -> Optional.of("machines");
            default -> Optional.empty();
        };
    }

    private static Optional<String> secondaryFeature(String actionType) {
        return switch (actionType) {
            case "market_page", "main_market", "sell_market_item",
                    "main_contracts", "contracts_back", "contract_detail", "complete_contract", "complete_emergency",
                    "deposit_machine_input", "withdraw_machine_input", "withdraw_machine_output", "reclaim_machine" -> Optional.of("storage");
            default -> Optional.empty();
        };
    }

    private static Optional<String> tertiaryFeature(String actionType) {
        return switch (actionType) {
            case "complete_emergency" -> Optional.of("maintenance");
            default -> Optional.empty();
        };
    }

    private static boolean enabled(Predicate<String> featureEnabled, String feature) {
        if (featureEnabled == null) {
            return true;
        }
        try {
            return featureEnabled.test(feature);
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
