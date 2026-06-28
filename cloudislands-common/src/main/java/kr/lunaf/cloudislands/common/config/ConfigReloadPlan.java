package kr.lunaf.cloudislands.common.config;

public final class ConfigReloadPlan {
    private ConfigReloadPlan() {
    }

    public static ReloadResult reload(String currentEffectiveYaml, String candidateYaml) {
        String rollbackBackup = currentEffectiveYaml == null ? "" : currentEffectiveYaml;
        ConfigValidationResult validation = ConfigV2Validator.validateYaml("reload-candidate", candidateYaml);
        if (!validation.valid()) {
            return new ReloadResult(false, rollbackBackup, rollbackBackup, validation);
        }
        return new ReloadResult(true, candidateYaml, rollbackBackup, validation);
    }

    public record ReloadResult(boolean applied, String effectiveYaml, String rollbackBackupYaml, ConfigValidationResult validation) {
        public ReloadResult {
            effectiveYaml = effectiveYaml == null ? "" : effectiveYaml;
            rollbackBackupYaml = rollbackBackupYaml == null ? "" : rollbackBackupYaml;
        }
    }
}
