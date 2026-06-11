package kr.lunaf.cloudislands.coreservice.upgrade;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;

public interface IslandUpgradeRepository {
    Optional<IslandUpgradeSnapshot> find(UUID islandId, String upgradeKey);
    List<IslandUpgradeSnapshot> list(UUID islandId);
    IslandUpgradeSnapshot setLevel(UUID islandId, String upgradeKey, UpgradeType type, int level);
}
