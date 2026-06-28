package kr.lunaf.cloudislands.coreservice.generator;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.generator.GeneratorRuleSnapshot;
import kr.lunaf.cloudislands.api.generator.IslandGeneratorSnapshot;

public interface IslandGeneratorRepository {
    IslandGeneratorSnapshot profile(UUID islandId);
    IslandGeneratorSnapshot setProfile(UUID islandId, String generatorKey, int level);
    List<GeneratorRuleSnapshot> rules(String generatorKey);
    List<GeneratorRuleSnapshot> setRules(String generatorKey, List<GeneratorRuleSnapshot> rules);
}
