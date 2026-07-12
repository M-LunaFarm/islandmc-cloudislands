package kr.lunaf.cloudislands.coreservice.limit;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;

public interface IslandLimitRepository {
    List<IslandLimitSnapshot> list(UUID islandId);
    IslandLimitSnapshot set(UUID islandId, String limitKey, long value, UUID updatedBy);
    IslandLimitSnapshot setAtLeast(UUID islandId, String limitKey, long minimumValue, UUID updatedBy);
    IslandLimitSnapshot add(UUID islandId, String limitKey, long delta, UUID updatedBy);
}
