package kr.lunaf.cloudislands.coreservice.warehouse;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandWarehouseItemSnapshot;

public interface IslandWarehouseRepository {
    ChangeResult deposit(UUID islandId, String materialKey, long amount);
    ChangeResult withdraw(UUID islandId, String materialKey, long amount);
    List<IslandWarehouseItemSnapshot> list(UUID islandId, int limit);

    record ChangeResult(boolean accepted, String code, IslandWarehouseItemSnapshot item) {}
}
