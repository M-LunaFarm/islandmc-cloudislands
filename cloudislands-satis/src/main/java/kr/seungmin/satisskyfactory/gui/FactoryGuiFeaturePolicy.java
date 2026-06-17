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
        if (!featureEnabled.test("gui")) {
            return Optional.of("gui");
        }
        Optional<String> primary = primaryFeature(actionType);
        if (primary.isPresent() && !featureEnabled.test(primary.get())) {
            return primary;
        }
        Optional<String> secondary = secondaryFeature(actionType);
        if (secondary.isPresent() && !featureEnabled.test(secondary.get())) {
            return secondary;
        }
        Optional<String> tertiary = tertiaryFeature(actionType);
        if (tertiary.isPresent() && !featureEnabled.test(tertiary.get())) {
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
                    "main_contracts", "contracts_back", "contract_detail", "complete_contract", "complete_emergency" -> Optional.of("storage");
            default -> Optional.empty();
        };
    }

    private static Optional<String> tertiaryFeature(String actionType) {
        return switch (actionType) {
            case "complete_emergency" -> Optional.of("maintenance");
            default -> Optional.empty();
        };
    }
}
