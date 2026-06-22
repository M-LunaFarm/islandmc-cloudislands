package kr.lunaf.cloudislands.paper.admin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;
import kr.lunaf.cloudislands.common.config.ConfigDiff;
import kr.lunaf.cloudislands.common.config.ConfigIssue;
import kr.lunaf.cloudislands.common.config.ConfigValidationResult;
import kr.lunaf.cloudislands.common.config.ConfigV2Validator;
import kr.lunaf.cloudislands.coreclient.AdminCoreConfigView;
import kr.lunaf.cloudislands.coreclient.AdminMaintenanceResultView;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperAgent;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperPlugin;
import kr.lunaf.cloudislands.paper.gui.GuiActionSchema;
import org.bukkit.command.CommandSender;

final class AdminConfigCommandHandler {
    private final CloudIslandsPaperAgent agent;
    private final CoreApiClient coreApiClient;
    private final AdminText text;
    private final CommandRunner runner;
    private final UsageSender usageSender;
    private final Function<AdminCoreConfigView, String> coreConfigFormatter;
    private final Function<AdminMaintenanceResultView, String> maintenanceFormatter;

    AdminConfigCommandHandler(
        CloudIslandsPaperAgent agent,
        CoreApiClient coreApiClient,
        AdminText text,
        CommandRunner runner,
        UsageSender usageSender,
        Function<AdminCoreConfigView, String> coreConfigFormatter,
        Function<AdminMaintenanceResultView, String> maintenanceFormatter
    ) {
        this.agent = agent;
        this.coreApiClient = coreApiClient;
        this.text = text == null ? (_key, fallback) -> fallback : text;
        this.runner = runner;
        this.usageSender = usageSender;
        this.coreConfigFormatter = coreConfigFormatter == null ? AdminCoreConfigView::toString : coreConfigFormatter;
        this.maintenanceFormatter = maintenanceFormatter == null ? AdminMaintenanceResultView::toString : maintenanceFormatter;
    }

    boolean handle(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("show")) {
            runner.run(sender, "Core config", coreApiClient.adminCoreConfig().config().thenApply(coreConfigFormatter));
            return true;
        }
        if (args[1].equalsIgnoreCase("validate")) {
            sender.sendMessage(configValidationMessage(validateConfigV2Bundle()));
            return true;
        }
        if (args[1].equalsIgnoreCase("diff")) {
            sender.sendMessage(configDiffMessage());
            return true;
        }
        if (args[1].equalsIgnoreCase("effective")) {
            sender.sendMessage(writeEffectiveConfig());
            return true;
        }
        if (args[1].equalsIgnoreCase("sources")) {
            sender.sendMessage(configSourcesMessage());
            return true;
        }
        if (args[1].equalsIgnoreCase("reload")) {
            ConfigValidationResult validation = validateConfigV2Bundle();
            if (!validation.valid()) {
                sender.sendMessage(configValidationMessage(validation));
                return true;
            }
            reloadRuntimeConfig();
            runner.run(sender, "Config reload", coreApiClient.adminMaintenance().reload().thenApply(maintenanceFormatter));
            return true;
        }
        usageSender.send(sender, List.of(
            "/ciadmin config",
            "/ciadmin config validate",
            "/ciadmin config diff",
            "/ciadmin config reload",
            "/ciadmin config effective",
            "/ciadmin config sources"
        ));
        return true;
    }

    void reloadRuntimeConfig() {
        if (agent.plugin() instanceof CloudIslandsPaperPlugin plugin) {
            plugin.reloadRuntimeConfig();
            return;
        }
        throw new IllegalStateException("CloudIslands Paper runtime config reload requires the Paper plugin instance");
    }

    String validationDiagnosticSection() {
        ConfigValidationResult validation = validateConfigV2Bundle();
        return "## config-validation\n"
            + "valid=" + validation.valid() + '\n'
            + "summary=" + redactDiagnostic(validation.summary()) + '\n';
    }

    String effectiveConfigDiagnosticSection() {
        try {
            return "## effective-config-redacted\n" + redactDiagnostic(effectiveConfigV2Yaml(true)) + '\n';
        } catch (RuntimeException exception) {
            return "## effective-config-redacted\nerror=" + exception.getClass().getSimpleName() + ':' + exception.getMessage() + '\n';
        }
    }

    private ConfigValidationResult validateConfigV2Bundle() {
        List<ConfigIssue> issues = new java.util.ArrayList<>();
        Path root = configV2Root();
        if (Files.notExists(root)) {
            issues.add(new ConfigIssue("CONFIG_V2_MISSING", root.toString(), "config-v2 directory is missing"));
            return new ConfigValidationResult(issues);
        }
        try (Stream<Path> files = Files.walk(root)) {
            files
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                .forEach(path -> issues.addAll(validateConfigV2File(root, path).issues()));
        } catch (IOException exception) {
            issues.add(new ConfigIssue("CONFIG_V2_READ_FAILED", root.toString(), exception.getMessage()));
        }
        return new ConfigValidationResult(issues);
    }

    private ConfigValidationResult validateConfigV2File(Path root, Path path) {
        String relative = root.relativize(path).toString();
        String yaml = readConfigFile(path);
        if (relative.contains("ui/menus/")) {
            return ConfigV2Validator.validateMenuYaml(relative, yaml, GuiActionSchema.registeredActionIds());
        }
        return ConfigV2Validator.validateYaml(relative, yaml);
    }

    private String configValidationMessage(ConfigValidationResult validation) {
        return validation.valid()
            ? text.get("admin-command-config-validate-ok", "Config v2 validation: valid")
            : text.get("admin-command-config-validate-failed-prefix", "Config v2 validation failed: ") + validation.summary();
    }

    private String configDiffMessage() {
        try {
            String current = currentConfigYaml();
            String candidate = candidateConfigYaml();
            ConfigDiff diff = ConfigDiff.between(current, candidate, List.of(
                "node.id",
                "node.role",
                "node.velocity-server-name",
                "core-api.base-url",
                "redis.uri",
                "storage.type",
                "security.forwarding-secret"
            ));
            return text.get("admin-command-config-diff-prefix", "Config diff: changed=") + diff.changedLines().size()
                + text.get("admin-command-config-diff-restart-prefix", " restartRequired=") + diff.restartRequired()
                + (diff.changedLines().isEmpty() ? "" : text.get("admin-command-config-diff-paths-prefix", " paths=") + String.join(",", diff.changedLines().stream().limit(12).toList()));
        } catch (RuntimeException exception) {
            return text.get("admin-command-config-diff-failed-prefix", "Config diff failed: ") + exception.getMessage();
        }
    }

    private String currentConfigYaml() {
        if (agent.plugin() instanceof CloudIslandsPaperPlugin plugin) {
            return plugin.runtimeConfig().sourceConfig().effectiveYaml();
        }
        return "";
    }

    private String candidateConfigYaml() {
        if (agent.plugin() instanceof CloudIslandsPaperPlugin plugin) {
            return plugin.loadRuntimeConfigSnapshot().sourceConfig().effectiveYaml();
        }
        return "";
    }

    private String writeEffectiveConfig() {
        try {
            Path output = agent.plugin().getDataFolder().toPath().resolve("generated").resolve("effective-config.yml");
            Files.createDirectories(output.getParent());
            Files.writeString(output, effectiveConfigV2Yaml(true));
            return text.get("admin-command-config-effective-written-prefix", "Effective config written: ") + output;
        } catch (IOException | RuntimeException exception) {
            return text.get("admin-command-config-effective-failed-prefix", "Effective config failed: ") + exception.getMessage();
        }
    }

    private String configSourcesMessage() {
        Path root = configV2Root();
        if (Files.notExists(root)) {
            return text.get("admin-command-config-sources-missing-prefix", "Config sources missing: ") + root;
        }
        try (Stream<Path> files = Files.walk(root)) {
            List<String> sources = files
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                .map(root::relativize)
                .map(Path::toString)
                .sorted()
                .toList();
            return text.get("admin-command-config-sources-prefix", "Config sources: ") + String.join(",", sources);
        } catch (IOException exception) {
            return text.get("admin-command-config-sources-failed-prefix", "Config sources failed: ") + exception.getMessage();
        }
    }

    private String effectiveConfigV2Yaml(boolean redact) {
        Path root = configV2Root();
        if (Files.notExists(root)) {
            return "";
        }
        try (Stream<Path> files = Files.walk(root)) {
            String effective = files
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                .sorted()
                .map(path -> "# source: " + root.relativize(path) + System.lineSeparator() + readConfigFile(path))
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("");
            return redact ? ConfigV2Validator.redactYaml(effective) : effective;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Path configV2Root() {
        Path data = agent.plugin().getDataFolder().toPath().resolve("config-v2");
        if (Files.exists(data)) {
            return data;
        }
        Path moduleResource = Path.of("src/main/resources/config-v2");
        if (Files.exists(moduleResource)) {
            return moduleResource;
        }
        Path repositoryResource = Path.of("cloudislands-paper/src/main/resources/config-v2");
        if (Files.exists(repositoryResource)) {
            return repositoryResource;
        }
        return data;
    }

    private String readConfigFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String redactDiagnostic(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
            .replaceAll("(?i)(token|secret|password|authorization|accessKey|secretKey)\\\"?\\s*[:=]\\s*\\\"?[^,\\n\\r\\\"]+", "$1=***")
            .replaceAll("ghp_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+", "***");
    }

    @FunctionalInterface
    interface AdminText {
        String get(String key, String fallback);
    }

    @FunctionalInterface
    interface CommandRunner {
        void run(CommandSender sender, String action, CompletableFuture<String> future);
    }

    @FunctionalInterface
    interface UsageSender {
        void send(CommandSender sender, List<String> commands);
    }
}
