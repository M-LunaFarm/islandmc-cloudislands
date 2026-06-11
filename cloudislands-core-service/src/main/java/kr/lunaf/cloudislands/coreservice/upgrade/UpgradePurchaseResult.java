package kr.lunaf.cloudislands.coreservice.upgrade;

import java.math.BigDecimal;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;

public record UpgradePurchaseResult(boolean accepted, String code, BigDecimal cost, IslandUpgradeSnapshot snapshot) {}
