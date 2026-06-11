package kr.lunaf.cloudislands.coreservice.audit;

import java.util.Map;
import java.util.UUID;

public interface AuditLogger {
    void log(UUID actorUuid, String actorType, String action, String targetType, String targetId, Map<String, String> payload);
}
