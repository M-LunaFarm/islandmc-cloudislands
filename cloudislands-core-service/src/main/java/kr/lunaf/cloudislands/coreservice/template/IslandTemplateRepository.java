package kr.lunaf.cloudislands.coreservice.template;

import java.util.Optional;
import java.util.List;

public interface IslandTemplateRepository {
    Optional<IslandTemplateSnapshot> find(String templateId);

    List<IslandTemplateSnapshot> list();

    IslandTemplateSnapshot upsert(IslandTemplateSnapshot template);

    default IslandTemplateSnapshot upsert(String templateId, String displayName, boolean enabled, String minNodeVersion) {
        return upsert(new IslandTemplateSnapshot(templateId, displayName, enabled, minNodeVersion));
    }

    boolean setEnabled(String templateId, boolean enabled);

    default boolean delete(String templateId) {
        return false;
    }

    default boolean reorder(String templateId, int sortOrder) {
        return false;
    }

    default boolean enabled(String templateId) {
        return find(templateId).map(IslandTemplateSnapshot::enabled).orElse(false);
    }
}
