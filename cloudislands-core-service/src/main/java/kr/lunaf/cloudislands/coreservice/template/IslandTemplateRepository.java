package kr.lunaf.cloudislands.coreservice.template;

import java.util.Optional;

public interface IslandTemplateRepository {
    Optional<IslandTemplateSnapshot> find(String templateId);

    default boolean enabled(String templateId) {
        return find(templateId).map(IslandTemplateSnapshot::enabled).orElse(false);
    }
}
