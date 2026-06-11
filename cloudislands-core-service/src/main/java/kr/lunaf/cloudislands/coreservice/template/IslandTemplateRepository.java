package kr.lunaf.cloudislands.coreservice.template;

import java.util.Optional;
import java.util.List;

public interface IslandTemplateRepository {
    Optional<IslandTemplateSnapshot> find(String templateId);

    List<IslandTemplateSnapshot> list();

    IslandTemplateSnapshot upsert(String templateId, String displayName, boolean enabled, String minNodeVersion);

    boolean setEnabled(String templateId, boolean enabled);

    default boolean enabled(String templateId) {
        return find(templateId).map(IslandTemplateSnapshot::enabled).orElse(false);
    }
}
