package kr.lunaf.cloudislands.coreservice.template;

import java.util.Map;
import java.util.Optional;

public final class InMemoryIslandTemplateRepository implements IslandTemplateRepository {
    private final Map<String, IslandTemplateSnapshot> templates = Map.of(
        "default", new IslandTemplateSnapshot("default", "Default Island", true, ""),
        "superiorskyblock2", new IslandTemplateSnapshot("superiorskyblock2", "SuperiorSkyblock2 Migration", true, "")
    );

    @Override
    public Optional<IslandTemplateSnapshot> find(String templateId) {
        String id = templateId == null || templateId.isBlank() ? "default" : templateId;
        return Optional.ofNullable(templates.get(id));
    }
}
