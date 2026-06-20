package kr.lunaf.cloudislands.common.config;

public final class ConfigReloadPlan {
    private ConfigReloadPlan() {
    }

    public static ReloadResult reload(String currentEffectiveYaml, String candidateYaml) {
        ConfigValidationResult validation = ConfigV2Validator.validateYaml("reload-candidate", candidateYaml);
        if (!validation.valid()) {
            return new ReloadResult(false, currentEffectiveYaml == null ? "" : currentEffectiveYaml, validation);
        }
        return new ReloadResult(true, candidateYaml, validation);
    }

    public record ReloadResult(boolean applied, String effectiveYaml, ConfigValidationResult validation) {
        public ReloadResult {
            effectiveYaml = effectiveYaml == null ? "" : effectiveYaml;
        }
    }
}
