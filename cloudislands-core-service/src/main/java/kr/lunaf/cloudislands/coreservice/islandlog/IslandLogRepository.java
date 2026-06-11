package kr.lunaf.cloudislands.coreservice.islandlog;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLogRecord;

public interface IslandLogRepository {
    void append(UUID islandId, UUID actorUuid, String action, Map<String, String> payload);
    List<IslandLogRecord> list(UUID islandId, int limit);
}
