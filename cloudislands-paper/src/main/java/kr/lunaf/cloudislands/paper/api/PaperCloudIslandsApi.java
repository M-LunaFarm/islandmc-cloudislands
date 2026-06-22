package kr.lunaf.cloudislands.paper.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.addon.CloudIslandsAddon;
import kr.lunaf.cloudislands.api.event.CloudEvent;
import kr.lunaf.cloudislands.api.model.AddonStateBulkLoadRequest;
import kr.lunaf.cloudislands.api.model.AddonStateBulkSaveRequest;
import kr.lunaf.cloudislands.api.model.AuditLogSnapshot;
import kr.lunaf.cloudislands.api.model.BlockValueSnapshot;
import kr.lunaf.cloudislands.api.model.ClaimedIslandJobSnapshot;
import kr.lunaf.cloudislands.api.model.CloudIslandsStatusSnapshot;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;
import kr.lunaf.cloudislands.api.model.CoreMaintenanceResult;
import kr.lunaf.cloudislands.api.model.GlobalEventBatchSnapshot;
import kr.lunaf.cloudislands.api.model.GlobalEventSnapshot;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.api.model.IslandActionResult;
import kr.lunaf.cloudislands.api.model.IslandBankChangeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBanSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBoundarySnapshot;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandChatResult;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandInviteActionResult;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
import kr.lunaf.cloudislands.api.model.IslandJobSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLevelSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandLogRecord;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandMissionSnapshot;
import kr.lunaf.cloudislands.api.model.IslandNodeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandPermissionRuleSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRegionSnapshot;
import kr.lunaf.cloudislands.api.model.IslandReviewRankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandReviewSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandRoleSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRuntimeJobType;
import kr.lunaf.cloudislands.api.model.IslandSizeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.api.model.IslandTemplateSnapshot;
import kr.lunaf.cloudislands.api.model.IslandVisitorStatsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandWarehouseItemSnapshot;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;
import kr.lunaf.cloudislands.api.model.IslandWorthSnapshot;
import kr.lunaf.cloudislands.api.model.JobRecoveryResult;
import kr.lunaf.cloudislands.api.model.MigrationRunSnapshot;
import kr.lunaf.cloudislands.api.model.MissionProviderDefinitionSnapshot;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.api.model.NodeLevelScanSnapshot;
import kr.lunaf.cloudislands.api.model.NodeStorageSnapshot;
import kr.lunaf.cloudislands.api.model.NodeSweepResult;
import kr.lunaf.cloudislands.api.model.PermissionResult;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;
import kr.lunaf.cloudislands.api.model.PlayerRouteSessionSnapshot;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteClearResult;
import kr.lunaf.cloudislands.api.model.RouteDebugSnapshot;
import kr.lunaf.cloudislands.api.model.RoutePlan;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.api.service.IslandAdminService;
import kr.lunaf.cloudislands.api.service.IslandAddonService;
import kr.lunaf.cloudislands.api.service.IslandCommandService;
import kr.lunaf.cloudislands.api.service.IslandEventService;
import kr.lunaf.cloudislands.api.service.IslandPermissionService;
import kr.lunaf.cloudislands.api.service.IslandQueryService;
import kr.lunaf.cloudislands.api.service.IslandRoutingService;
import kr.lunaf.cloudislands.api.service.IslandRuntimeService;
import kr.lunaf.cloudislands.api.service.IslandStatusService;
import kr.lunaf.cloudislands.api.service.IslandEventService.GlobalEventSubscription;
import kr.lunaf.cloudislands.api.service.PlayerIslandService;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;
import kr.lunaf.cloudislands.api.upgrade.UpgradePurchaseSnapshot;
import kr.lunaf.cloudislands.api.upgrade.UpgradeRuleSnapshot;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.AddonStateClient;
import kr.lunaf.cloudislands.coreclient.AdminAuditEntryView;
import kr.lunaf.cloudislands.coreclient.AdminEventStreamView;
import kr.lunaf.cloudislands.coreclient.AdminEventView;
import kr.lunaf.cloudislands.coreclient.AdminIslandRuntimeView;
import kr.lunaf.cloudislands.coreclient.AdminMaintenanceResultView;
import kr.lunaf.cloudislands.coreclient.AdminNodeActionView;
import kr.lunaf.cloudislands.coreclient.AdminRouteClearView;
import kr.lunaf.cloudislands.coreclient.AdminRouteDebugView;
import kr.lunaf.cloudislands.coreclient.AdminRouteSessionView;
import kr.lunaf.cloudislands.coreclient.AdminRouteTicketView;
import kr.lunaf.cloudislands.coreclient.BlockValueActionView;
import kr.lunaf.cloudislands.coreclient.BlockValueView;
import kr.lunaf.cloudislands.coreclient.ChatActionView;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.CoreMutationContext;
import kr.lunaf.cloudislands.coreclient.CoreMutationMetadata;
import kr.lunaf.cloudislands.coreclient.EnvironmentActionView;
import kr.lunaf.cloudislands.coreclient.HomeWarpActionView;
import kr.lunaf.cloudislands.coreclient.IslandLifecycleActionView;
import kr.lunaf.cloudislands.coreclient.IslandVisitorStatsView;
import kr.lunaf.cloudislands.coreclient.JobActionView;
import kr.lunaf.cloudislands.coreclient.JobRecoveryView;
import kr.lunaf.cloudislands.coreclient.JobView;
import kr.lunaf.cloudislands.coreclient.LevelView;
import kr.lunaf.cloudislands.coreclient.MemberActionView;
import kr.lunaf.cloudislands.coreclient.MutationResult;
import kr.lunaf.cloudislands.coreclient.PermissionActionView;
import kr.lunaf.cloudislands.coreclient.PermissionAssignmentView;
import kr.lunaf.cloudislands.coreclient.PlayerProfileView;
import kr.lunaf.cloudislands.coreclient.ProgressionMissionCompletionView;
import kr.lunaf.cloudislands.coreclient.ProgressionRankingEntryView;
import kr.lunaf.cloudislands.coreclient.ProgressionReviewRankingEntryView;
import kr.lunaf.cloudislands.coreclient.ProgressionUpgradePurchaseView;
import kr.lunaf.cloudislands.coreclient.ReviewListView;
import kr.lunaf.cloudislands.coreclient.ReviewView;
import kr.lunaf.cloudislands.coreclient.RoutePublishView;
import kr.lunaf.cloudislands.coreclient.RuntimeActionView;
import kr.lunaf.cloudislands.coreclient.SettingsActionView;
import kr.lunaf.cloudislands.coreclient.TemplateView;
import kr.lunaf.cloudislands.coreclient.UpgradeRuleView;
import kr.lunaf.cloudislands.coreclient.WarehouseItemView;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperAgent;
import kr.lunaf.cloudislands.paper.config.PaperAddonConfigFile;
import kr.lunaf.cloudislands.paper.config.PaperAddonConfigStore;
import kr.lunaf.cloudislands.paper.config.PaperRuntimeConfig;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class PaperCloudIslandsApi implements CloudIslandsApi {
    private final QueryService query;
    private final PlayerService players;
    private final RoutingService routing;
    private final PermissionService permissions;
    private final RuntimeService runtime;
    private final StatusService status;
    private final EventService events;
    private final AdminService admin;
    private final CommandService commands;
    private final AddonService addons;

    public PaperCloudIslandsApi(CoreApiClient client, CloudIslandsPaperAgent agent) {
        this(client, agent, PaperRuntimeConfig.defaults());
    }

    public PaperCloudIslandsApi(CoreApiClient client, CloudIslandsPaperAgent agent, PaperRuntimeConfig config) {
        PaperRuntimeConfig safeConfig = config == null ? PaperRuntimeConfig.defaults() : config;
        this.query = new QueryService(client, agent);
        this.players = new PlayerService(client, query);
        this.routing = new RoutingService(client);
        this.permissions = new PermissionService(agent);
        this.runtime = new RuntimeService(client);
        this.status = new StatusService(agent, safeConfig);
        this.events = new EventService(client, agent.plugin());
        this.admin = new AdminService(client, safeConfig.migration().superiorSkyblock2Enabled());
        this.commands = new CommandService(client);
        this.addons = new AddonService(client, agent.plugin(), safeConfig, events);
        agent.cacheInvalidator().setAddonStateInvalidator(invalidation -> addons.invalidateAddonStateCache(invalidation.addonId(), invalidation.islandId()));
    }

    @Override
    public IslandQueryService islands() {
        return query;
    }

    @Override
    public PlayerIslandService players() {
        return players;
    }

    @Override
    public IslandRoutingService routing() {
        return routing;
    }

    @Override
    public IslandPermissionService permissions() {
        return permissions;
    }

    @Override
    public IslandRuntimeService runtime() {
        return runtime;
    }

    @Override
    public IslandStatusService status() {
        return status;
    }

    @Override
    public IslandEventService events() {
        return events;
    }

    @Override
    public IslandAdminService admin() {
        return admin;
    }

    @Override
    public IslandCommandService commands() {
        return commands;
    }

    @Override
    public IslandAddonService addons() {
        return addons;
    }

    private static <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
        return CoreMutationContext.with(CoreMutationMetadata.request(auditAction), operation);
    }

    private static <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
        return CoreMutationContext.with(CoreMutationMetadata.idempotent(auditAction), operation);
    }

    private static final class AddonService implements IslandAddonService {
        private final CoreApiClient coreClient;
        private final AddonStateClient addonStateClient;
        private final Plugin plugin;
        private final PaperRuntimeConfig runtimeConfig;
        private final PaperAddonConfigStore addonConfig;
        private final IslandEventService events;
        private final Map<String, AddonRegistration> registrations = new ConcurrentHashMap<>();
        private final Map<String, CloudIslandsAddon> addonObjects = new ConcurrentHashMap<>();
        private final Map<String, CloudIslandsAddonSnapshot> addons = new ConcurrentHashMap<>();
        private final Map<String, Map<String, String>> addonStates = new ConcurrentHashMap<>();
        private final Map<String, Map<UUID, Map<String, String>>> addonIslandStates = new ConcurrentHashMap<>();
        private GlobalEventSubscription eventSubscription;
        private boolean eventSubscriptionStarting;

        private AddonService(CoreApiClient coreClient, Plugin plugin, PaperRuntimeConfig runtimeConfig, IslandEventService events) {
            this.coreClient = coreClient;
            this.addonStateClient = coreClient.addonStates();
            this.plugin = plugin;
            this.runtimeConfig = runtimeConfig == null ? PaperRuntimeConfig.defaults() : runtimeConfig;
            this.addonConfig = new PaperAddonConfigStore(PaperAddonConfigFile.fromPlugin(plugin));
            this.events = events;
        }

        @Override
        public CompletableFuture<CloudIslandsAddonSnapshot> register(String id, String displayName, String version, boolean enabled, Map<String, Boolean> features, Map<String, String> metadata) {
            String safeId = safeRegistrationId(id);
            CloudIslandsAddon previous = addonObjects.remove(safeId);
            if (previous != null) {
                notifyUnregistered(previous);
            }
            AddonRegistration registration = new AddonRegistration(safeId, safeRegistrationDisplayName(displayName, safeId), safeRegistrationVersion(version), enabled, Instant.now(), copyBooleanMap(features), copyStringMap(metadata));
            registrations.put(safeId, registration);
            CloudIslandsAddonSnapshot snapshot = snapshot(registration);
            addons.put(safeId, snapshot);
            syncEventSubscription();
            return CompletableFuture.completedFuture(snapshot);
        }

        private String safeRegistrationId(String id) {
            return id == null || id.isBlank() ? "manual-addon" : id;
        }

        private String safeRegistrationDisplayName(String displayName, String id) {
            return displayName == null || displayName.isBlank() ? id : displayName;
        }

        private String safeRegistrationVersion(String version) {
            return version == null || version.isBlank() ? "unknown" : version;
        }

        @Override
        public CompletableFuture<CloudIslandsAddonSnapshot> register(CloudIslandsAddon addon) {
            String id = safeAddonId(addon);
            AddonRegistration registration = new AddonRegistration(id, safeAddonDisplayName(addon, id), safeAddonVersion(addon), safeAddonEnabledByDefault(addon), Instant.now(), safeAddonFeatures(addon, id), safeAddonMetadata(addon, id));
            registrations.put(id, registration);
            CloudIslandsAddon previous = addon == null ? addonObjects.remove(id) : addonObjects.put(id, addon);
            if (previous != null && previous != addon) {
                notifyUnregistered(previous);
            }
            CloudIslandsAddonSnapshot snapshot = snapshot(registration);
            addons.put(id, snapshot);
            if (addon != null) {
                notifyRegistered(addon, snapshot);
            }
            syncEventSubscription();
            return CompletableFuture.completedFuture(snapshot);
        }

        private String safeAddonId(CloudIslandsAddon addon) {
            if (addon == null) {
                return fallbackAddonId(addon);
            }
            try {
                String id = addon.addonId();
                return id == null || id.isBlank() ? addon.getClass().getName() : id;
            } catch (RuntimeException exception) {
                String id = fallbackAddonId(addon);
                plugin.getLogger().warning("CloudIslands addon id callback failed for " + id + ": " + exception.getMessage());
                return id;
            }
        }

        private String fallbackAddonId(CloudIslandsAddon addon) {
            return addon == null ? "null-addon" : addon.getClass().getName();
        }

        private String safeAddonDisplayName(CloudIslandsAddon addon, String id) {
            if (addon == null) {
                return id;
            }
            try {
                String displayName = addon.addonDisplayName();
                return displayName == null || displayName.isBlank() ? id : displayName;
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("CloudIslands addon display callback failed for " + id + ": " + exception.getMessage());
                return id;
            }
        }

        private String safeAddonVersion(CloudIslandsAddon addon) {
            if (addon == null) {
                return "unknown";
            }
            try {
                String version = addon.addonVersion();
                return version == null || version.isBlank() ? "unknown" : version;
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("CloudIslands addon version callback failed for " + fallbackAddonId(addon) + ": " + exception.getMessage());
                return "unknown";
            }
        }

        private boolean safeAddonEnabledByDefault(CloudIslandsAddon addon) {
            if (addon == null) {
                return false;
            }
            try {
                return addon.enabledByDefault();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("CloudIslands addon enabled callback failed for " + fallbackAddonId(addon) + ": " + exception.getMessage());
                return false;
            }
        }

        private Map<String, Boolean> safeAddonFeatures(CloudIslandsAddon addon, String id) {
            if (addon == null) {
                return Map.of();
            }
            try {
                return copyBooleanMap(addon.addonFeatures());
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("CloudIslands addon feature callback failed for " + id + ": " + exception.getMessage());
                return Map.of();
            }
        }

        private Map<String, String> safeAddonMetadata(CloudIslandsAddon addon, String id) {
            if (addon == null) {
                return Map.of("metadata-error", "NullAddon");
            }
            Map<String, String> metadata = new HashMap<>();
            try {
                metadata.putAll(copyStringMap(addon.addonStandardMetadata()));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("CloudIslands addon standard metadata callback failed for " + id + ": " + exception.getMessage());
                metadata.put("metadata-standard-error", exception.getClass().getSimpleName());
            }
            try {
                metadata.putAll(copyStringMap(addon.addonMetadata()));
                return Map.copyOf(metadata);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("CloudIslands addon metadata callback failed for " + id + ": " + exception.getMessage());
                metadata.put("metadata-error", exception.getClass().getSimpleName());
                return Map.copyOf(metadata);
            }
        }

        private Map<String, Boolean> copyBooleanMap(Map<String, Boolean> source) {
            if (source == null || source.isEmpty()) {
                return Map.of();
            }
            Map<String, Boolean> copy = new HashMap<>();
            source.forEach((key, value) -> {
                if (key != null && value != null) {
                    copy.put(key, value);
                }
            });
            return Map.copyOf(copy);
        }

        private Map<String, String> copyStringMap(Map<String, String> source) {
            if (source == null || source.isEmpty()) {
                return Map.of();
            }
            Map<String, String> copy = new HashMap<>();
            source.forEach((key, value) -> {
                if (key != null && value != null) {
                    copy.put(key, value);
                }
            });
            return Map.copyOf(copy);
        }

        private CloudIslandsAddonSnapshot snapshot(AddonRegistration registration) {
            String id = registration.id();
            boolean addonDefaultEnabled = registration.enabled();
            boolean configEnabled = configuredAddonEnabled(registration);
            boolean enabled = addonDefaultEnabled && configEnabled;
            Map<String, Boolean> configuredFeatures = configuredFeatures(registration);
            Map<String, Boolean> visibleFeatures = enabled ? effectiveFeatures(configuredFeatures, registration.metadata()) : disabledFeatures(configuredFeatures);
            return new CloudIslandsAddonSnapshot(id, registration.displayName(), registration.version(), enabled, registration.registeredAt(), Instant.now(), configuredFeatures, visibleFeatures, effectiveMetadata(id, registration.metadata(), addonDefaultEnabled, configEnabled));
        }

        private boolean configuredAddonEnabled(AddonRegistration registration) {
            return addonConfig.addonEnabled(registration.id(), parentConfigAliases(registration.metadata()));
        }

        private Map<String, Boolean> configuredFeatures(AddonRegistration registration) {
            Map<String, Boolean> features = registration.features();
            Map<String, Boolean> effective = addonConfig.addonFeatures(registration.id(), parentConfigAliases(registration.metadata()), features);
            applyFeatureAliases(effective, registration.metadata());
            return effective;
        }

        private Map<String, Boolean> effectiveFeatures(Map<String, Boolean> configuredFeatures, Map<String, String> metadata) {
            Map<String, Boolean> effective = new HashMap<>(configuredFeatures == null ? Map.of() : configuredFeatures);
            applyFeatureAliases(effective, metadata);
            applyFeatureDependencies(effective, metadata);
            return effective;
        }

        private void applyFeatureAliases(Map<String, Boolean> features, Map<String, String> metadata) {
            String aliases = metadata.getOrDefault("feature-aliases", "");
            for (String pair : aliases.split(",")) {
                String[] parts = pair.split(":", 2);
                if (parts.length != 2) {
                    continue;
                }
                String alias = parts[0];
                String canonical = parts[1];
                boolean enabled = features.getOrDefault(alias, true) && features.getOrDefault(canonical, true);
                if (features.containsKey(alias) || features.containsKey(canonical)) {
                    features.put(alias, enabled);
                }
            }
        }

        private void applyFeatureDependencies(Map<String, Boolean> features, Map<String, String> metadata) {
            java.util.Set<String> keys = new java.util.LinkedHashSet<>(features.keySet());
            keys.addAll(AddonFeatureAliases.dependencies(metadata).keySet());
            keys.forEach(feature -> features.put(AddonFeatureAliases.normalize(metadata, feature),
                    AddonFeatureAliases.featureEnabled(metadata, features, feature)));
        }

        private Map<String, Boolean> disabledFeatures(Map<String, Boolean> features) {
            Map<String, Boolean> disabled = new HashMap<>();
            for (String key : (features == null ? Map.<String, Boolean>of() : features).keySet()) {
                disabled.put(key, false);
            }
            return disabled;
        }

        private Map<String, String> effectiveMetadata(String id, Map<String, String> metadata, boolean addonDefaultEnabled, boolean parentEnabled) {
            Map<String, String> effective = new HashMap<>(metadata == null ? Map.of() : metadata);
            effective.putIfAbsent("source-node", runtimeConfig.node().id());
            effective.putIfAbsent("addon-registry-policy", "external-addons-config-gated-removable-and-state-preserving");
            effective.putIfAbsent("addon-packaging", "external-plugin");
            effective.putIfAbsent("addon-runtime-owns-islands", "false");
            effective.putIfAbsent("addon-removal-safe", "true");
            effective.putIfAbsent("addon-data-retention", "preserve-addon-state-by-addon-id-and-island-uuid");
            effective.putIfAbsent("addon-state-storage", "core-api-with-paper-local-fallback");
            effective.putIfAbsent("addon-state-unregister-policy", "preserve-core-and-local-fallback-state");
            effective.putIfAbsent("addon-state-clear-policy", "explicit-clear-only");
            effective.putIfAbsent("addon-reinstall-policy", "reuse-preserved-state-by-addon-id-and-island-uuid");
            effective.putIfAbsent("addon-event-source", "cloudislands-global-event-stream");
            effective.putIfAbsent("addon-event-delivery", "typed-cloud-event-callbacks-through-cloudislands-api");
            effective.putIfAbsent("addon-event-failure-policy", "addon-callback-exceptions-are-logged-and-isolated");
            effective.putIfAbsent("addon-event-feature-gating-policy", "disabled-addon-features-do-not-receive-matching-runtime-events,node-state-uses-lifecycle-gate,core-cache-and-reload-use-addon-enabled-gate");
            effective.putIfAbsent("addon-route-event-feature-gate", "route-events&&addon-state");
            effective.putIfAbsent("addon-lifecycle-events", "island-pre-create,island-created,island-pre-activate,island-activation-requested,island-activated,island-deactivation-requested,island-deactivated,island-migration-requested,island-migrated,island-delete-requested,island-deleted,island-delete-backup-failed,island-restore-requested,island-restored,island-reset,island-recovery-required,island-repaired,island-runtime-changed,island-pre-visit,island-visited,island-invite-changed,island-member-joined,island-member-left,island-member-changed,island-renamed,island-access-changed,island-visitor-ban-changed,island-visitor-kicked,island-flag-changed,island-permission-checked,island-permission-changed,island-role-changed,island-role-catalog-changed,island-ownership-changed,island-chat-sent,island-blocks-changed,island-block-value-changed,island-mission-progress,island-mission-completed,island-level-recalculate,island-worth-changed,island-upgrade-changed,island-limit-changed,island-biome-changed,island-home-changed,island-warp-created,island-warp-deleted,island-warp-changed,island-bank-changed,island-snapshot-requested,island-snapshot-created,island-template-changed,node-state-changed,route-ticket-created,route-session-published,route-ticket-consumed,route-ticket-failed,route-ticket-cleared,addon-state-changed,core-cache-cleared,core-reloaded");
            effective.put("addon-default-enabled", Boolean.toString(addonDefaultEnabled));
            effective.put("parent-enabled", Boolean.toString(parentEnabled));
            if (!addonDefaultEnabled) {
                effective.put("disabled-reason", "addon-default");
            } else if (!parentEnabled) {
                effective.put("disabled-reason", "parent-config");
            } else {
                effective.put("disabled-reason", "none");
            }
            List<String> parentPaths = addonConfig.configuredParentPaths(id, parentConfigAliases(metadata));
            effective.put("parent-config-path", parentPaths.isEmpty() ? "default" : String.join(",", parentPaths));
            return effective;
        }

        @Override
        public CompletableFuture<Void> unregister(String id) {
            String safeId = safeRegistrationId(id);
            CloudIslandsAddon addon = addonObjects.remove(safeId);
            if (addon != null) {
                notifyUnregistered(addon);
            }
            registrations.remove(safeId);
            addons.remove(safeId);
            syncEventSubscription();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Optional<CloudIslandsAddonSnapshot>> get(String id) {
            return CompletableFuture.completedFuture(Optional.ofNullable(addons.get(safeRegistrationId(id))));
        }

        @Override
        public CompletableFuture<List<CloudIslandsAddonSnapshot>> list() {
            return CompletableFuture.completedFuture(addons.values().stream()
                .sorted(Comparator.comparing(CloudIslandsAddonSnapshot::id))
                .toList());
        }

        @Override
        public CompletableFuture<Optional<CloudIslandsAddonSnapshot>> refresh(String id) {
            String safeId = safeRegistrationId(id);
            AddonRegistration registration = registrations.get(safeId);
            if (registration == null) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            CloudIslandsAddonSnapshot snapshot = snapshot(registration);
            addons.put(safeId, snapshot);
            CloudIslandsAddon addon = addonObjects.get(safeId);
            if (addon != null) {
                notifyReloaded(addon, snapshot);
            }
            syncEventSubscription();
            return CompletableFuture.completedFuture(Optional.of(snapshot));
        }

        @Override
        public CompletableFuture<List<CloudIslandsAddonSnapshot>> refreshAll() {
            registrations.values().forEach(registration -> {
                CloudIslandsAddonSnapshot snapshot = snapshot(registration);
                addons.put(registration.id(), snapshot);
                CloudIslandsAddon addon = addonObjects.get(registration.id());
                if (addon != null) {
                    notifyReloaded(addon, snapshot);
                }
            });
            addons.keySet().removeIf(id -> !registrations.containsKey(id));
            syncEventSubscription();
            return list();
        }

        @Override
        public CompletableFuture<Optional<CloudIslandsAddonSnapshot>> setEnabled(String id, boolean enabled) {
            String safeId = safeRegistrationId(id);
            if (!registrations.containsKey(safeId)) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            AddonRegistration registration = registrations.get(safeId);
            addonConfig.setEnabled(safeId, parentConfigAliases(registration.metadata()), enabled);
            return refresh(safeId);
        }

        @Override
        public CompletableFuture<Optional<CloudIslandsAddonSnapshot>> setFeature(String id, String feature, boolean enabled) {
            String safeId = safeRegistrationId(id);
            AddonRegistration registration = registrations.get(safeId);
            if (registration == null) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            String normalizedFeature = normalizeFeature(registration, feature);
            if (!registeredFeatureKnown(registration, feature, normalizedFeature)) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            String configFeature = configFeatureKey(registration, feature, normalizedFeature);
            List<String> aliases = parentConfigAliases(registration.metadata());
            addonConfig.setFeature(safeId, aliases, configFeature, enabled);
            if (configFeature.equals(normalizedFeature)) {
                clearFeatureAliases(safeId, aliases, registration, normalizedFeature);
            }
            addonConfig.saveAndReload();
            return refresh(safeId);
        }

        private String configFeatureKey(AddonRegistration registration, String requestedFeature, String normalizedFeature) {
            String requested = requestedFeature == null ? "" : requestedFeature.trim();
            if (requested.isBlank()) {
                return normalizedFeature;
            }
            Map<String, Boolean> features = registration.features() == null ? Map.of() : registration.features();
            Map<String, String> dependencies = AddonFeatureAliases.dependencies(registration.metadata());
            if (features.containsKey(requested)
                    || dependencies.containsKey(requested)
                    || AddonFeatureAliases.aliasesFor(registration.metadata(), normalizedFeature).contains(requested)) {
                return requested;
            }
            return normalizedFeature;
        }

        private void clearFeatureAliases(String id, List<String> parentAliases, AddonRegistration registration, String canonicalFeature) {
            addonConfig.clearFeatureAliases(id, parentAliases, AddonFeatureAliases.aliasesFor(registration.metadata(), canonicalFeature));
        }

        private List<String> parentConfigAliases(Map<String, String> metadata) {
            String aliases = metadata == null ? "" : metadata.getOrDefault("parent-config-aliases", "");
            List<String> values = new ArrayList<>();
            for (String alias : aliases.split(",")) {
                String safeAlias = alias == null ? "" : alias.trim();
                if (!safeAlias.isBlank() && !safeAlias.contains("..") && !safeAlias.startsWith(".") && !safeAlias.endsWith(".")) {
                    values.add(safeAlias);
                }
            }
            return values;
        }

        private boolean registeredFeatureKnown(AddonRegistration registration, String requestedFeature, String normalizedFeature) {
            Map<String, Boolean> features = registration.features() == null ? Map.of() : registration.features();
            String requested = requestedFeature == null ? "" : requestedFeature.trim();
            String normalized = normalizedFeature == null ? "" : normalizedFeature;
            if (features.containsKey(normalized) || features.containsKey(requested)) {
                return true;
            }
            if (AddonFeatureAliases.aliasesFor(registration.metadata(), normalized).contains(requested)) {
                return true;
            }
            Map<String, String> dependencies = AddonFeatureAliases.dependencies(registration.metadata());
            return dependencies.containsKey(normalized)
                || dependencies.containsKey(requested)
                || dependencies.containsValue(normalized)
                || dependencies.containsValue(requested);
        }

        private String normalizeFeature(AddonRegistration registration, String feature) {
            return AddonFeatureAliases.normalize(registration.metadata(), feature);
        }

        @Override
        public CompletableFuture<Boolean> isEnabled(String id) {
            return CompletableFuture.completedFuture(Optional.ofNullable(addons.get(safeRegistrationId(id))).map(CloudIslandsAddonSnapshot::enabled).orElse(false));
        }

        @Override
        public CompletableFuture<Map<String, String>> state(String id) {
            String safeId = safeRegistrationId(id);
            return addonStateClient.state(safeId)
                .thenApply(state -> {
                    addonStates.put(safeId, state);
                    writeAddonState(safeId, state);
                    return state;
                })
                .exceptionally(_error -> readAddonState(safeId));
        }

        @Override
        public CompletableFuture<Map<String, String>> putState(String id, Map<String, String> values) {
            String safeId = safeRegistrationId(id);
            if (values == null || values.isEmpty()) {
                return state(safeId);
            }
            if (!addonAcceptsGlobalStateWrites(safeId)) {
                return state(safeId);
            }
            Map<String, String> localState = new HashMap<>(readAddonState(safeId));
            Map<String, String> changedState = new HashMap<>();
            values.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    localState.put(key, value);
                    changedState.put(key, value);
                }
            });
            if (changedState.isEmpty()) {
                return CompletableFuture.completedFuture(Map.copyOf(localState));
            }
            writeAddonState(safeId, localState);
            return mutate("addon.state.put", () -> addonStateClient.putState(safeId, changedState))
                .thenApply(state -> {
                    addonStates.put(safeId, state);
                    writeAddonState(safeId, state);
                    return state;
                })
                .exceptionally(_error -> Map.copyOf(localState));
        }

        @Override
        public CompletableFuture<Map<String, String>> putState(String id, Map<String, String> values, Map<String, Map<String, String>> tables) {
            String safeId = safeRegistrationId(id);
            Map<String, String> merged = new HashMap<>();
            Map<String, String> safeValues = new HashMap<>();
            if (values != null) {
                values.forEach((key, value) -> {
                    if (key != null && !key.isBlank() && value != null) {
                        String safeKey = key.trim();
                        safeValues.put(safeKey, value);
                        merged.put(safeKey, value);
                    }
                });
            }
            Map<String, Map<String, String>> safeTables = new HashMap<>();
            if (tables != null) {
                tables.forEach((table, tableValues) -> {
                    Map<String, String> safeTableValues = new HashMap<>();
                    if (tableValues != null) {
                        tableValues.forEach((key, value) -> {
                            if (key != null && !key.isBlank() && value != null) {
                                safeTableValues.put(key.trim(), value);
                            }
                        });
                    }
                    if (table != null && !table.isBlank() && !safeTableValues.isEmpty()) {
                        safeTables.put(table.trim(), Map.copyOf(safeTableValues));
                        merged.putAll(tableStateValues(table, safeTableValues));
                    }
                });
            }
            if (merged.isEmpty()) {
                return state(safeId);
            }
            if (!addonAcceptsGlobalStateWrites(safeId)) {
                return state(safeId);
            }
            Map<String, String> localState = new HashMap<>(readAddonState(safeId));
            localState.putAll(merged);
            writeAddonState(safeId, localState);
            return mutate("addon.state.save", () -> addonStateClient.saveState(safeId, safeValues, safeTables))
                .thenApply(state -> {
                    addonStates.put(safeId, state);
                    writeAddonState(safeId, state);
                    return state;
                })
                .exceptionally(_error -> Map.copyOf(localState));
        }

        @Override
        public CompletableFuture<Map<String, String>> bulkSaveState(String id, Map<String, String> values, Map<String, Map<String, String>> tables) {
            String safeId = safeRegistrationId(id);
            Map<String, String> merged = new HashMap<>();
            Map<String, String> safeValues = new HashMap<>();
            if (values != null) {
                values.forEach((key, value) -> {
                    if (key != null && !key.isBlank() && value != null) {
                        String safeKey = key.trim();
                        safeValues.put(safeKey, value);
                        merged.put(safeKey, value);
                    }
                });
            }
            Map<String, Map<String, String>> safeTables = new HashMap<>();
            if (tables != null) {
                tables.forEach((table, tableValues) -> {
                    Map<String, String> safeTableValues = new HashMap<>();
                    if (tableValues != null) {
                        tableValues.forEach((key, value) -> {
                            if (key != null && !key.isBlank() && value != null) {
                                safeTableValues.put(key.trim(), value);
                            }
                        });
                    }
                    if (table != null && !table.isBlank() && !safeTableValues.isEmpty()) {
                        safeTables.put(table.trim(), Map.copyOf(safeTableValues));
                        merged.putAll(tableStateValues(table, safeTableValues));
                    }
                });
            }
            if (merged.isEmpty()) {
                return state(safeId);
            }
            if (!addonAcceptsGlobalStateWrites(safeId)) {
                return state(safeId);
            }
            Map<String, String> localState = new HashMap<>(readAddonState(safeId));
            localState.putAll(merged);
            writeAddonState(safeId, localState);
            return mutate("addon.state.table-key-value.bulk-save", () -> addonStateClient.tableKeyValueBulkSaveState(safeId, safeValues, safeTables))
                .thenApply(state -> {
                    addonStates.put(safeId, state);
                    writeAddonState(safeId, state);
                    return state;
                })
                .exceptionally(_error -> Map.copyOf(localState));
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkSaveState(String id, Map<String, String> values, Map<String, Map<String, String>> tables) {
            return bulkSaveState(id, values, tables);
        }

        @Override
        public CompletableFuture<Map<String, String>> bulkSaveTableKeyValueState(String id, Map<String, String> values, Map<String, Map<String, String>> tables) {
            return bulkSaveState(id, values, tables);
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkSaveAliasState(String id, Map<String, String> values, Map<String, Map<String, String>> tables) {
            return bulkSaveState(id, values, tables);
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkState(String id, Map<String, String> values, Map<String, Map<String, String>> tables) {
            return bulkSaveState(id, values, tables);
        }

        @Override
        public CompletableFuture<Map<String, String>> bulkTableKeyValueState(String id, Map<String, String> values, Map<String, Map<String, String>> tables) {
            return bulkSaveState(id, values, tables);
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkSaveState(AddonStateBulkSaveRequest request) {
            if (request == null) {
                return CompletableFuture.completedFuture(Map.of());
            }
            if (request.islandScoped()) {
                return tableKeyValueBulkSaveIslandState(request);
            }
            return bulkSaveState(request.addonId(), request.tableScoped() ? Map.of() : request.values(), request.tablesWithScopedTable());
        }

        @Override
        public CompletableFuture<Map<String, String>> tableBulkState(String id, Map<String, Map<String, String>> tables) {
            String safeId = safeRegistrationId(id);
            Map<String, String> merged = new HashMap<>();
            Map<String, Map<String, String>> safeTables = new HashMap<>();
            if (tables != null) {
                tables.forEach((table, tableValues) -> {
                    Map<String, String> safeTableValues = new HashMap<>();
                    if (tableValues != null) {
                        tableValues.forEach((key, value) -> {
                            if (key != null && !key.isBlank() && value != null) {
                                safeTableValues.put(key.trim(), value);
                            }
                        });
                    }
                    if (table != null && !table.isBlank() && !safeTableValues.isEmpty()) {
                        safeTables.put(table.trim(), Map.copyOf(safeTableValues));
                        merged.putAll(tableStateValues(table, safeTableValues));
                    }
                });
            }
            if (merged.isEmpty()) {
                return state(safeId);
            }
            if (!addonAcceptsGlobalStateWrites(safeId)) {
                return state(safeId);
            }
            Map<String, String> localState = new HashMap<>(readAddonState(safeId));
            localState.putAll(merged);
            writeAddonState(safeId, localState);
            return mutate("addon.state.table.bulk", () -> addonStateClient.tableBulkState(safeId, safeTables))
                .thenApply(state -> {
                    addonStates.put(safeId, state);
                    writeAddonState(safeId, state);
                    return state;
                })
                .exceptionally(_error -> Map.copyOf(localState));
        }

        @Override
        public CompletableFuture<Map<String, String>> bulkTableState(String id, Map<String, Map<String, String>> tables) {
            return tableBulkState(id, tables);
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkLoadState(String id, String table) {
            String safeId = safeRegistrationId(id);
            String safeTable = safeTableName(table);
            if (safeTable.isBlank()) {
                return CompletableFuture.completedFuture(Map.of());
            }
            return addonStateClient.tableKeyValueBulkLoadState(safeId, safeTable)
                .exceptionally(_error -> tableValuesFromState(readAddonState(safeId), safeTable));
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkLoadState(AddonStateBulkLoadRequest request) {
            if (request == null) {
                return CompletableFuture.completedFuture(Map.of());
            }
            if (request.islandScoped()) {
                return tableKeyValueBulkLoadIslandState(request);
            }
            return tableKeyValueBulkLoadState(request.addonId(), request.table());
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkSaveState(String id, String table, Map<String, String> values) {
            return bulkSaveState(id, Map.of(), table == null ? Map.of() : Map.of(table, values == null ? Map.<String, String>of() : values));
        }

        @Override
        public CompletableFuture<Map<String, String>> bulkSaveTableKeyValueState(String id, String table, Map<String, String> values) {
            return tableKeyValueBulkSaveState(id, table, values);
        }

        @Override
        public CompletableFuture<Map<String, String>> saveTableKeyValueState(String id, String table, Map<String, String> values) {
            return tableKeyValueBulkSaveState(id, table, values);
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkSaveAliasState(String id, String table, Map<String, String> values) {
            return tableKeyValueBulkSaveState(id, table, values);
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkState(String id, String table, Map<String, String> values) {
            return tableKeyValueBulkSaveState(id, table, values);
        }

        @Override
        public CompletableFuture<Map<String, String>> bulkTableKeyValueState(String id, String table, Map<String, String> values) {
            return tableKeyValueBulkState(id, table, values);
        }

        @Override
        public CompletableFuture<Map<String, String>> clearTableState(String id, String table) {
            String safeId = safeRegistrationId(id);
            if (table == null || table.isBlank()) {
                return state(safeId);
            }
            if (!addonAcceptsGlobalStateWrites(safeId)) {
                return state(safeId);
            }
            String prefix = tableStatePrefix(table);
            Map<String, String> localState = new HashMap<>(readAddonState(safeId));
            localState.keySet().removeIf(key -> key.startsWith(prefix));
            writeAddonState(safeId, localState);
            return mutateIdempotent("addon.state.table.clear", () -> addonStateClient.clearTableState(safeId, table))
                .thenApply(state -> {
                    addonStates.put(safeId, state);
                    writeAddonState(safeId, state);
                    return state;
                })
                .exceptionally(_error -> Map.copyOf(localState));
        }

        @Override
        public CompletableFuture<Map<String, String>> replaceTableState(String id, String table, Map<String, String> values) {
            String safeId = safeRegistrationId(id);
            if (table == null || table.isBlank()) {
                return state(safeId);
            }
            if (!addonAcceptsGlobalStateWrites(safeId)) {
                return state(safeId);
            }
            Map<String, String> tableValues = tableStateValues(table, values);
            String prefix = tableStatePrefix(table);
            Map<String, String> localState = new HashMap<>(readAddonState(safeId));
            localState.keySet().removeIf(key -> key.startsWith(prefix));
            localState.putAll(tableValues);
            writeAddonState(safeId, localState);
            return mutateIdempotent("addon.state.table.replace", () -> addonStateClient.replaceTableState(safeId, table, values))
                .thenApply(state -> {
                    addonStates.put(safeId, state);
                    writeAddonState(safeId, state);
                    return state;
                })
                .exceptionally(_error -> Map.copyOf(localState));
        }

        private boolean addonAcceptsGlobalStateWrites(String id) {
            CloudIslandsAddonSnapshot snapshot = addons.get(safeRegistrationId(id));
            return snapshot != null && snapshot.addonStateWritesEnabled();
        }

        private String tableStatePrefix(String table) {
            return IslandAddonService.TABLE_STATE_KEY_PREFIX + safeTableName(table) + "/";
        }

        private Map<String, String> tableStateValues(String table, Map<String, String> values) {
            if (values == null || values.isEmpty()) {
                return Map.of();
            }
            String prefix = tableStatePrefix(table);
            Map<String, String> state = new HashMap<>();
            values.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    state.put(prefix + key.trim(), value);
                }
            });
            return Map.copyOf(state);
        }

        private Map<String, String> tableValuesFromState(Map<String, String> state, String table) {
            if (state == null || state.isEmpty() || table == null || table.isBlank()) {
                return Map.of();
            }
            String prefix = tableStatePrefix(table);
            Map<String, String> values = new HashMap<>();
            state.forEach((key, value) -> {
                if (key != null && key.startsWith(prefix) && value != null) {
                    values.put(key.substring(prefix.length()), value);
                }
            });
            return Map.copyOf(values);
        }

        private String safeTableName(String table) {
            String value = table == null ? "" : table.trim();
            if (value.startsWith(IslandAddonService.TABLE_STATE_KEY_PREFIX)) {
                value = value.substring(IslandAddonService.TABLE_STATE_KEY_PREFIX.length());
            }
            while (value.startsWith("/")) {
                value = value.substring(1);
            }
            while (value.endsWith("/")) {
                value = value.substring(0, value.length() - 1);
            }
            return value;
        }

        @Override
        public CompletableFuture<Map<String, String>> removeState(String id, String key) {
            String safeId = safeRegistrationId(id);
            Map<String, String> state = new HashMap<>(readAddonState(safeId));
            if (key == null) {
                return CompletableFuture.completedFuture(Map.copyOf(state));
            }
            state.remove(key);
            writeAddonState(safeId, state);
            return mutateIdempotent("addon.state.remove", () -> addonStateClient.removeState(safeId, key))
                .thenApply(coreState -> {
                    addonStates.put(safeId, coreState);
                    writeAddonState(safeId, coreState);
                    return coreState;
                })
                .exceptionally(_error -> Map.copyOf(state));
        }

        @Override
        public CompletableFuture<Void> clearState(String id) {
            String safeId = safeRegistrationId(id);
            addonStates.remove(safeId);
            try {
                Files.deleteIfExists(addonStatePath(safeId));
            } catch (IOException exception) {
                plugin.getLogger().warning("CloudIslands addon state clear failed for " + safeId + ": " + exception.getMessage());
            }
            return mutateIdempotent("addon.state.clear", () -> addonStateClient.clearState(safeId)).exceptionally(_error -> null);
        }

        @Override
        public CompletableFuture<Map<String, String>> islandState(String id, UUID islandId) {
            String safeId = safeRegistrationId(id);
            if (islandId == null) {
                return CompletableFuture.completedFuture(Map.of());
            }
            return addonStateClient.islandState(safeId, islandId)
                .thenApply(state -> {
                    writeAddonIslandState(safeId, islandId, state);
                    return state;
                })
                .exceptionally(_error -> readAddonIslandState(safeId, islandId));
        }

        @Override
        public CompletableFuture<Map<String, String>> putIslandState(String id, UUID islandId, Map<String, String> values) {
            String safeId = safeRegistrationId(id);
            if (islandId == null) {
                return CompletableFuture.completedFuture(Map.of());
            }
            if (values == null || values.isEmpty()) {
                return islandState(safeId, islandId);
            }
            if (!addonAcceptsIslandStateWrites(safeId)) {
                return islandState(safeId, islandId);
            }
            Map<String, String> localState = new HashMap<>(readAddonIslandState(safeId, islandId));
            Map<String, String> changedState = new HashMap<>();
            values.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    localState.put(key, value);
                    changedState.put(key, value);
                }
            });
            if (changedState.isEmpty()) {
                return CompletableFuture.completedFuture(Map.copyOf(localState));
            }
            writeAddonIslandState(safeId, islandId, localState);
            return mutate("addon.island-state.put", () -> addonStateClient.putIslandState(safeId, islandId, changedState))
                .thenApply(state -> {
                    writeAddonIslandState(safeId, islandId, state);
                    return state;
                })
                .exceptionally(_error -> Map.copyOf(localState));
        }

        @Override
        public CompletableFuture<Map<String, String>> putIslandState(String id, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
            String safeId = safeRegistrationId(id);
            if (islandId == null) {
                return CompletableFuture.completedFuture(Map.of());
            }
            Map<String, String> merged = new HashMap<>();
            Map<String, String> safeValues = new HashMap<>();
            if (values != null) {
                values.forEach((key, value) -> {
                    if (key != null && !key.isBlank() && value != null) {
                        String safeKey = key.trim();
                        safeValues.put(safeKey, value);
                        merged.put(safeKey, value);
                    }
                });
            }
            Map<String, Map<String, String>> safeTables = new HashMap<>();
            if (tables != null) {
                tables.forEach((table, tableValues) -> {
                    Map<String, String> safeTableValues = new HashMap<>();
                    if (tableValues != null) {
                        tableValues.forEach((key, value) -> {
                            if (key != null && !key.isBlank() && value != null) {
                                safeTableValues.put(key.trim(), value);
                            }
                        });
                    }
                    if (table != null && !table.isBlank() && !safeTableValues.isEmpty()) {
                        safeTables.put(table.trim(), Map.copyOf(safeTableValues));
                        merged.putAll(tableStateValues(table, safeTableValues));
                    }
                });
            }
            if (merged.isEmpty()) {
                return islandState(safeId, islandId);
            }
            if (!addonAcceptsIslandStateWrites(safeId)) {
                return islandState(safeId, islandId);
            }
            Map<String, String> localState = new HashMap<>(readAddonIslandState(safeId, islandId));
            localState.putAll(merged);
            writeAddonIslandState(safeId, islandId, localState);
            return mutate("addon.island-state.save", () -> addonStateClient.saveIslandState(safeId, islandId, safeValues, safeTables))
                .thenApply(state -> {
                    writeAddonIslandState(safeId, islandId, state);
                    return state;
                })
                .exceptionally(_error -> Map.copyOf(localState));
        }

        @Override
        public CompletableFuture<Map<String, String>> bulkSaveIslandState(String id, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
            String safeId = safeRegistrationId(id);
            if (islandId == null) {
                return CompletableFuture.completedFuture(Map.of());
            }
            Map<String, String> merged = new HashMap<>();
            Map<String, String> safeValues = new HashMap<>();
            if (values != null) {
                values.forEach((key, value) -> {
                    if (key != null && !key.isBlank() && value != null) {
                        String safeKey = key.trim();
                        safeValues.put(safeKey, value);
                        merged.put(safeKey, value);
                    }
                });
            }
            Map<String, Map<String, String>> safeTables = new HashMap<>();
            if (tables != null) {
                tables.forEach((table, tableValues) -> {
                    Map<String, String> safeTableValues = new HashMap<>();
                    if (tableValues != null) {
                        tableValues.forEach((key, value) -> {
                            if (key != null && !key.isBlank() && value != null) {
                                safeTableValues.put(key.trim(), value);
                            }
                        });
                    }
                    if (table != null && !table.isBlank() && !safeTableValues.isEmpty()) {
                        safeTables.put(table.trim(), Map.copyOf(safeTableValues));
                        merged.putAll(tableStateValues(table, safeTableValues));
                    }
                });
            }
            if (merged.isEmpty()) {
                return islandState(safeId, islandId);
            }
            if (!addonAcceptsIslandStateWrites(safeId)) {
                return islandState(safeId, islandId);
            }
            Map<String, String> localState = new HashMap<>(readAddonIslandState(safeId, islandId));
            localState.putAll(merged);
            writeAddonIslandState(safeId, islandId, localState);
            return mutate("addon.island-state.table-key-value.bulk-save", () -> addonStateClient.tableKeyValueBulkSaveIslandState(safeId, islandId, safeValues, safeTables))
                .thenApply(state -> {
                    writeAddonIslandState(safeId, islandId, state);
                    return state;
                })
                .exceptionally(_error -> Map.copyOf(localState));
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkSaveIslandState(String id, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
            return bulkSaveIslandState(id, islandId, values, tables);
        }

        @Override
        public CompletableFuture<Map<String, String>> bulkSaveIslandTableKeyValueState(String id, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
            return bulkSaveIslandState(id, islandId, values, tables);
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkSaveAliasIslandState(String id, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
            return bulkSaveIslandState(id, islandId, values, tables);
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkIslandState(String id, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
            return bulkSaveIslandState(id, islandId, values, tables);
        }

        @Override
        public CompletableFuture<Map<String, String>> bulkIslandTableKeyValueState(String id, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
            return bulkSaveIslandState(id, islandId, values, tables);
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkSaveIslandState(AddonStateBulkSaveRequest request) {
            if (request == null || !request.islandScoped()) {
                return CompletableFuture.completedFuture(Map.of());
            }
            return bulkSaveIslandState(request.addonId(), request.islandId(), request.tableScoped() ? Map.of() : request.values(), request.tablesWithScopedTable());
        }

        @Override
        public CompletableFuture<Map<String, String>> tableBulkIslandState(String id, UUID islandId, Map<String, Map<String, String>> tables) {
            String safeId = safeRegistrationId(id);
            if (islandId == null) {
                return CompletableFuture.completedFuture(Map.of());
            }
            Map<String, String> merged = new HashMap<>();
            Map<String, Map<String, String>> safeTables = new HashMap<>();
            if (tables != null) {
                tables.forEach((table, tableValues) -> {
                    Map<String, String> safeTableValues = new HashMap<>();
                    if (tableValues != null) {
                        tableValues.forEach((key, value) -> {
                            if (key != null && !key.isBlank() && value != null) {
                                safeTableValues.put(key.trim(), value);
                            }
                        });
                    }
                    if (table != null && !table.isBlank() && !safeTableValues.isEmpty()) {
                        safeTables.put(table.trim(), Map.copyOf(safeTableValues));
                        merged.putAll(tableStateValues(table, safeTableValues));
                    }
                });
            }
            if (merged.isEmpty()) {
                return islandState(safeId, islandId);
            }
            if (!addonAcceptsIslandStateWrites(safeId)) {
                return islandState(safeId, islandId);
            }
            Map<String, String> localState = new HashMap<>(readAddonIslandState(safeId, islandId));
            localState.putAll(merged);
            writeAddonIslandState(safeId, islandId, localState);
            return mutate("addon.island-state.table.bulk", () -> addonStateClient.tableBulkIslandState(safeId, islandId, safeTables))
                .thenApply(state -> {
                    writeAddonIslandState(safeId, islandId, state);
                    return state;
                })
                .exceptionally(_error -> Map.copyOf(localState));
        }

        @Override
        public CompletableFuture<Map<String, String>> bulkIslandTableState(String id, UUID islandId, Map<String, Map<String, String>> tables) {
            return tableBulkIslandState(id, islandId, tables);
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkLoadIslandState(String id, UUID islandId, String table) {
            String safeId = safeRegistrationId(id);
            String safeTable = safeTableName(table);
            if (islandId == null || safeTable.isBlank()) {
                return CompletableFuture.completedFuture(Map.of());
            }
            return addonStateClient.tableKeyValueBulkLoadIslandState(safeId, islandId, safeTable)
                .exceptionally(_error -> tableValuesFromState(readAddonIslandState(safeId, islandId), safeTable));
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkLoadIslandState(AddonStateBulkLoadRequest request) {
            if (request == null || !request.islandScoped()) {
                return CompletableFuture.completedFuture(Map.of());
            }
            return tableKeyValueBulkLoadIslandState(request.addonId(), request.islandId(), request.table());
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkSaveIslandState(String id, UUID islandId, String table, Map<String, String> values) {
            return bulkSaveIslandState(id, islandId, Map.of(), table == null ? Map.of() : Map.of(table, values == null ? Map.<String, String>of() : values));
        }

        @Override
        public CompletableFuture<Map<String, String>> bulkSaveIslandTableKeyValueState(String id, UUID islandId, String table, Map<String, String> values) {
            return tableKeyValueBulkSaveIslandState(id, islandId, table, values);
        }

        @Override
        public CompletableFuture<Map<String, String>> saveIslandTableKeyValueState(String id, UUID islandId, String table, Map<String, String> values) {
            return tableKeyValueBulkSaveIslandState(id, islandId, table, values);
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkSaveAliasIslandState(String id, UUID islandId, String table, Map<String, String> values) {
            return tableKeyValueBulkSaveIslandState(id, islandId, table, values);
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkIslandState(String id, UUID islandId, String table, Map<String, String> values) {
            return tableKeyValueBulkSaveIslandState(id, islandId, table, values);
        }

        @Override
        public CompletableFuture<Map<String, String>> bulkIslandTableKeyValueState(String id, UUID islandId, String table, Map<String, String> values) {
            return tableKeyValueBulkIslandState(id, islandId, table, values);
        }

        @Override
        public CompletableFuture<Map<String, String>> clearIslandTableState(String id, UUID islandId, String table) {
            String safeId = safeRegistrationId(id);
            if (islandId == null || table == null || table.isBlank()) {
                return islandState(safeId, islandId);
            }
            if (!addonAcceptsIslandStateWrites(safeId)) {
                return islandState(safeId, islandId);
            }
            String prefix = tableStatePrefix(table);
            Map<String, String> localState = new HashMap<>(readAddonIslandState(safeId, islandId));
            localState.keySet().removeIf(key -> key.startsWith(prefix));
            writeAddonIslandState(safeId, islandId, localState);
            return mutateIdempotent("addon.island-state.table.clear", () -> addonStateClient.clearIslandTableState(safeId, islandId, table))
                .thenApply(state -> {
                    writeAddonIslandState(safeId, islandId, state);
                    return state;
                })
                .exceptionally(_error -> Map.copyOf(localState));
        }

        @Override
        public CompletableFuture<Map<String, String>> replaceIslandTableState(String id, UUID islandId, String table, Map<String, String> values) {
            String safeId = safeRegistrationId(id);
            if (islandId == null || table == null || table.isBlank()) {
                return islandState(safeId, islandId);
            }
            if (!addonAcceptsIslandStateWrites(safeId)) {
                return islandState(safeId, islandId);
            }
            Map<String, String> tableValues = tableStateValues(table, values);
            String prefix = tableStatePrefix(table);
            Map<String, String> localState = new HashMap<>(readAddonIslandState(safeId, islandId));
            localState.keySet().removeIf(key -> key.startsWith(prefix));
            localState.putAll(tableValues);
            writeAddonIslandState(safeId, islandId, localState);
            return mutateIdempotent("addon.island-state.table.replace", () -> addonStateClient.replaceIslandTableState(safeId, islandId, table, values))
                .thenApply(state -> {
                    writeAddonIslandState(safeId, islandId, state);
                    return state;
                })
                .exceptionally(_error -> Map.copyOf(localState));
        }

        @Override
        public CompletableFuture<Map<String, String>> removeIslandState(String id, UUID islandId, String key) {
            String safeId = safeRegistrationId(id);
            if (islandId == null) {
                return CompletableFuture.completedFuture(Map.of());
            }
            if (key == null) {
                return islandState(safeId, islandId);
            }
            if (!addonAcceptsIslandStateWrites(safeId)) {
                return islandState(safeId, islandId);
            }
            Map<String, String> state = new HashMap<>(readAddonIslandState(safeId, islandId));
            state.remove(key);
            writeAddonIslandState(safeId, islandId, state);
            return mutateIdempotent("addon.island-state.remove", () -> addonStateClient.removeIslandState(safeId, islandId, key))
                .thenApply(coreState -> {
                    writeAddonIslandState(safeId, islandId, coreState);
                    return coreState;
                })
                .exceptionally(_error -> Map.copyOf(state));
        }

        @Override
        public CompletableFuture<Void> clearIslandState(String id, UUID islandId) {
            if (islandId == null) {
                return CompletableFuture.completedFuture(null);
            }
            String safeId = safeRegistrationId(id);
            if (!addonAcceptsIslandStateWrites(safeId)) {
                return CompletableFuture.completedFuture(null);
            }
            Map<UUID, Map<String, String>> islandStates = addonIslandStates.get(safeId);
            if (islandStates != null) {
                islandStates.remove(islandId);
            }
            try {
                Files.deleteIfExists(addonIslandStatePath(safeId, islandId));
            } catch (IOException exception) {
                plugin.getLogger().warning("CloudIslands addon island state clear failed for " + safeId + "/" + islandId + ": " + exception.getMessage());
            }
            return mutateIdempotent("addon.island-state.clear", () -> addonStateClient.clearIslandState(safeId, islandId)).exceptionally(_error -> null);
        }

        private boolean addonAcceptsIslandStateWrites(String id) {
            CloudIslandsAddonSnapshot snapshot = addons.get(safeRegistrationId(id));
            return snapshot != null && snapshot.addonStateWritesEnabled();
        }

        private Map<String, String> readAddonState(String id) {
            String safeId = safeRegistrationId(id);
            Map<String, String> cached = addonStates.get(safeId);
            if (cached != null) {
                return cached;
            }
            Path path = addonStatePath(safeId);
            if (!Files.exists(path)) {
                addonStates.put(safeId, Map.of());
                return Map.of();
            }
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException exception) {
                plugin.getLogger().warning("CloudIslands addon state read failed for " + safeId + ": " + exception.getMessage());
                return Map.of();
            }
            Map<String, String> state = new HashMap<>();
            for (String key : properties.stringPropertyNames()) {
                state.put(key, properties.getProperty(key));
            }
            Map<String, String> immutable = Map.copyOf(state);
            addonStates.put(safeId, immutable);
            return immutable;
        }

        private void writeAddonState(String id, Map<String, String> state) {
            String safeId = safeRegistrationId(id);
            Path path = addonStatePath(safeId);
            try {
                Files.createDirectories(path.getParent());
                Properties properties = new Properties();
                state.forEach((key, value) -> {
                    if (key != null && !key.isBlank() && value != null) {
                        properties.setProperty(key, value);
                    }
                });
                try (OutputStream output = Files.newOutputStream(path)) {
                    properties.store(output, "CloudIslands addon state");
                }
                addonStates.put(safeId, Map.copyOf(state));
            } catch (IOException exception) {
                plugin.getLogger().warning("CloudIslands addon state write failed for " + safeId + ": " + exception.getMessage());
            }
        }

        private Map<String, String> readAddonIslandState(String id, UUID islandId) {
            if (islandId == null) {
                return Map.of();
            }
            String safeId = safeRegistrationId(id);
            Map<UUID, Map<String, String>> islandStates = addonIslandStates.computeIfAbsent(safeId, _key -> new ConcurrentHashMap<>());
            Map<String, String> cached = islandStates.get(islandId);
            if (cached != null) {
                return cached;
            }
            Path path = addonIslandStatePath(safeId, islandId);
            if (!Files.exists(path)) {
                islandStates.put(islandId, Map.of());
                return Map.of();
            }
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException exception) {
                plugin.getLogger().warning("CloudIslands addon island state read failed for " + safeId + "/" + islandId + ": " + exception.getMessage());
                return Map.of();
            }
            Map<String, String> state = new HashMap<>();
            for (String key : properties.stringPropertyNames()) {
                state.put(key, properties.getProperty(key));
            }
            Map<String, String> immutable = Map.copyOf(state);
            islandStates.put(islandId, immutable);
            return immutable;
        }

        private void writeAddonIslandState(String id, UUID islandId, Map<String, String> state) {
            if (islandId == null) {
                return;
            }
            String safeId = safeRegistrationId(id);
            Path path = addonIslandStatePath(safeId, islandId);
            try {
                Files.createDirectories(path.getParent());
                Properties properties = new Properties();
                state.forEach((key, value) -> {
                    if (key != null && !key.isBlank() && value != null) {
                        properties.setProperty(key, value);
                    }
                });
                try (OutputStream output = Files.newOutputStream(path)) {
                    properties.store(output, "CloudIslands addon island state");
                }
                addonIslandStates.computeIfAbsent(safeId, _key -> new ConcurrentHashMap<>()).put(islandId, Map.copyOf(state));
            } catch (IOException exception) {
                plugin.getLogger().warning("CloudIslands addon island state write failed for " + safeId + "/" + islandId + ": " + exception.getMessage());
            }
        }

        private void invalidateAddonStateCache(String id, UUID islandId) {
            String safeId = safeRegistrationId(id);
            if (id == null || id.isBlank()) {
                addonStates.clear();
                addonIslandStates.clear();
                return;
            }
            if (islandId == null) {
                addonStates.remove(safeId);
                Map<UUID, Map<String, String>> islandStates = addonIslandStates.get(safeId);
                if (islandStates != null) {
                    islandStates.clear();
                }
                return;
            }
            Map<UUID, Map<String, String>> islandStates = addonIslandStates.get(safeId);
            if (islandStates != null) {
                islandStates.remove(islandId);
            }
        }

        private Path addonStatePath(String id) {
            return plugin.getDataFolder().toPath()
                .resolve("addons")
                .resolve(stateDirectoryName(id))
                .resolve("state.properties");
        }

        private Path addonIslandStatePath(String id, UUID islandId) {
            return plugin.getDataFolder().toPath()
                .resolve("addons")
                .resolve(stateDirectoryName(id))
                .resolve("islands")
                .resolve(islandId + ".properties");
        }

        private String stateDirectoryName(String id) {
            return safeRegistrationId(id).replaceAll("[^A-Za-z0-9._-]", "_");
        }

        private synchronized void syncEventSubscription() {
            if (hasEnabledAddonObject()) {
                ensureEventSubscription();
                return;
            }
            stopEventSubscription();
        }

        private synchronized void ensureEventSubscription() {
            if (eventSubscription != null || eventSubscriptionStarting) {
                return;
            }
            eventSubscriptionStarting = true;
            events.listGlobalEventBatch(1).thenAccept(batch -> {
                synchronized (this) {
                    if (!hasEnabledAddonObject()) {
                        eventSubscriptionStarting = false;
                        return;
                    }
                    if (eventSubscription != null) {
                        eventSubscriptionStarting = false;
                        return;
                    }
                    eventSubscription = events.subscribeTypedGlobalEvents(batch.latestSequence(), 64, 20L, this::dispatchCloudEvents);
                    eventSubscriptionStarting = false;
                }
            }).exceptionally(_error -> {
                synchronized (this) {
                    eventSubscriptionStarting = false;
                }
                return null;
            });
        }

        private synchronized void stopEventSubscription() {
            if (eventSubscription == null) {
                return;
            }
            eventSubscription.close();
            eventSubscription = null;
        }

        private boolean hasEnabledAddonObject() {
            return addonObjects.keySet().stream()
                .map(addons::get)
                .anyMatch(snapshot -> snapshot != null && snapshot.enabled());
        }

        private void dispatchCloudEvents(List<CloudEvent> events) {
            if (events == null || events.isEmpty()) {
                return;
            }
            List<Map.Entry<String, CloudIslandsAddon>> targets = List.copyOf(addonObjects.entrySet());
            for (CloudEvent event : events) {
                for (Map.Entry<String, CloudIslandsAddon> target : targets) {
                    CloudIslandsAddonSnapshot snapshot = addons.get(target.getKey());
                    if (!addonAcceptsEvent(snapshot, event)) {
                        continue;
                    }
                    CloudIslandsAddon addon = target.getValue();
                    try {
                        addon.onCloudEvent(event);
                    } catch (RuntimeException exception) {
                        plugin.getLogger().warning("CloudIslands addon event callback failed for " + addonIdForLog(addon) + ": " + exception.getMessage());
                    }
                }
            }
        }

        private boolean addonAcceptsEvent(CloudIslandsAddonSnapshot snapshot, CloudEvent event) {
            if (snapshot == null || !snapshot.enabled() || event == null) {
                return false;
            }
            String eventName = event.getClass().getSimpleName();
            for (String feature : eventFeatureGates(eventName)) {
                if (!snapshot.acceptsRuntimeFeature(feature, true)) {
                    return false;
                }
            }
            return true;
        }

        private List<String> eventFeatureGates(String eventName) {
            if (eventName == null || eventName.isBlank()) {
                return List.of();
            }
            if (eventName.startsWith("RouteTicket") || eventName.startsWith("RouteSession")) {
                return List.of("route-events", "addon-state");
            }
            if (eventName.equals("AddonStateChangeEvent")) {
                return List.of("addon-state");
            }
            if (eventName.equals("NodeStateChangedEvent")) {
                return List.of("lifecycle");
            }
            if (eventName.startsWith("Core")) {
                return List.of();
            }
            if (eventName.equals("IslandPreCreateEvent")
                || eventName.equals("IslandCreatedEvent")
                || eventName.equals("IslandPreActivateEvent")
                || eventName.equals("IslandActivationRequestEvent")
                || eventName.equals("IslandActivatedEvent")
                || eventName.equals("IslandDeactivationRequestEvent")
                || eventName.equals("IslandDeactivatedEvent")
                || eventName.equals("IslandDeleteRequestEvent")
                || eventName.equals("IslandDeletedEvent")
                || eventName.equals("IslandRuntimeChangeEvent")) {
                return List.of("lifecycle");
            }
            if (eventName.startsWith("IslandMember")
                || eventName.equals("IslandInviteChangeEvent")
                || eventName.equals("IslandOwnershipChangeEvent")) {
                return List.of("members");
            }
            if (eventName.startsWith("IslandPermission")
                || eventName.startsWith("IslandRole")
                || eventName.equals("IslandAccessChangeEvent")
                || eventName.startsWith("IslandVisitor")
                || eventName.equals("IslandFlagChangeEvent")) {
                return List.of("permissions");
            }
            if (eventName.startsWith("IslandMission")) {
                return List.of("missions");
            }
            if (eventName.startsWith("IslandUpgrade") || eventName.equals("IslandLimitChangeEvent")) {
                return List.of("upgrades");
            }
            if (eventName.equals("IslandLevelRecalculateEvent")
                || eventName.equals("IslandWorthChangeEvent")
                || eventName.equals("IslandBlocksChangeEvent")
                || eventName.equals("IslandBlockValueChangeEvent")) {
                return List.of("level-values");
            }
            if (eventName.equals("IslandBankChangeEvent")) {
                return List.of("storage");
            }
            if (eventName.startsWith("IslandWarp") || eventName.equals("IslandHomeChangeEvent")) {
                return List.of("warps");
            }
            if (eventName.equals("IslandBiomeChangeEvent")) {
                return List.of("biomes");
            }
            if (eventName.equals("IslandChatSentEvent")) {
                return List.of("chat");
            }
            if (eventName.equals("IslandTemplateChangeEvent")) {
                return List.of("templates");
            }
            if (eventName.startsWith("Island")) {
                return List.of("lifecycle");
            }
            return List.of();
        }

        private void notifyRegistered(CloudIslandsAddon addon, CloudIslandsAddonSnapshot snapshot) {
            try {
                addon.onAddonRegistered(snapshot);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("CloudIslands addon register callback failed for " + addonIdForLog(addon) + ": " + exception.getMessage());
            }
        }

        private void notifyReloaded(CloudIslandsAddon addon, CloudIslandsAddonSnapshot snapshot) {
            try {
                addon.onAddonReloaded(snapshot);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("CloudIslands addon reload callback failed for " + addonIdForLog(addon) + ": " + exception.getMessage());
            }
        }

        private void notifyUnregistered(CloudIslandsAddon addon) {
            try {
                addon.onAddonUnregistered();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("CloudIslands addon unregister callback failed for " + addonIdForLog(addon) + ": " + exception.getMessage());
            }
        }

        private String addonIdForLog(CloudIslandsAddon addon) {
            try {
                String id = addon.addonId();
                return id == null || id.isBlank() ? addon.getClass().getName() : id;
            } catch (RuntimeException ignored) {
                return addon.getClass().getName();
            }
        }

        private record AddonRegistration(
            String id,
            String displayName,
            String version,
            boolean enabled,
            Instant registeredAt,
            Map<String, Boolean> features,
            Map<String, String> metadata
        ) {}
    }

    private static final class QueryService implements IslandQueryService {
        private final CoreApiClient client;
        private final CloudIslandsPaperAgent agent;

        private QueryService(CoreApiClient client, CloudIslandsPaperAgent agent) {
            this.client = client;
            this.agent = agent;
        }

        @Override
        public CompletableFuture<Optional<IslandSnapshot>> getIsland(UUID islandId) {
            return client.islands().getIsland(islandId).thenApply(PaperCloudIslandsApi::island);
        }

        @Override
        public CompletableFuture<Optional<IslandSnapshot>> getIslandByOwner(UUID ownerUuid) {
            return client.islands().getIslandByOwner(ownerUuid).thenApply(PaperCloudIslandsApi::island);
        }

        @Override
        public CompletableFuture<Optional<IslandSnapshot>> getIslandAt(String worldName, int blockX, int blockY, int blockZ) {
            return agent.protection().islandAt(worldName, blockX, blockZ)
                .map(this::getIsland)
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()));
        }

        @Override
        public CompletableFuture<Optional<IslandRegionSnapshot>> getRegion(UUID islandId) {
            return CompletableFuture.completedFuture(agent.protection().region(islandId).map(PaperCloudIslandsApi::region));
        }

        @Override
        public CompletableFuture<IslandRuntimeSnapshot> getRuntime(UUID islandId) {
            return client.adminIslands().runtime(islandId).thenApply(PaperCloudIslandsApi::runtime);
        }

        @Override
        public CompletableFuture<List<IslandMemberSnapshot>> getMembers(UUID islandId) {
            return client.islands().memberSnapshots(islandId);
        }

        @Override
        public CompletableFuture<List<IslandHomeSnapshot>> getHomes(UUID islandId) {
            return client.homeWarps().homeSnapshots(islandId);
        }

        @Override
        public CompletableFuture<List<IslandWarpSnapshot>> getWarps(UUID islandId) {
            return client.homeWarps().warpSnapshots(islandId);
        }

        @Override
        public CompletableFuture<List<IslandWarpSnapshot>> getPublicWarps(int limit) {
            return client.homeWarps().publicWarpSnapshots(limit, "", "");
        }

        @Override
        public CompletableFuture<List<IslandPermissionRuleSnapshot>> getPermissionRules(UUID islandId) {
            return client.permissionQueries().permissions(islandId).thenApply(views -> permissionRules(islandId, views));
        }

        @Override
        public CompletableFuture<List<IslandRoleSnapshot>> getRoles(UUID islandId) {
            return client.permissionQueries().roles(islandId).thenApply(views -> roles(islandId, views));
        }

        @Override
        public CompletableFuture<IslandBoundarySnapshot> getBoundary(UUID islandId) {
            return client.islands().getIsland(islandId).thenApply(PaperCloudIslandsApi::boundary);
        }

        @Override
        public CompletableFuture<IslandSizeSnapshot> getSize(UUID islandId) {
            return client.islands().getIsland(islandId).thenApply(PaperCloudIslandsApi::size);
        }

        @Override
        public CompletableFuture<IslandWorthSnapshot> getWorth(UUID islandId) {
            return client.islands().getIsland(islandId).thenApply(PaperCloudIslandsApi::worth);
        }

        @Override
        public CompletableFuture<List<IslandBanSnapshot>> getBans(UUID islandId) {
            return client.members().banSnapshots(islandId);
        }

        @Override
        public CompletableFuture<List<IslandInviteSnapshot>> getPendingInvites(UUID playerUuid) {
            return client.members().inviteSnapshots(playerUuid);
        }

        @Override
        public CompletableFuture<IslandFlagsSnapshot> getFlags(UUID islandId) {
            return client.environment().flags(islandId);
        }

        @Override
        public CompletableFuture<IslandBiomeSnapshot> getBiome(UUID islandId) {
            return client.environment().biome(islandId);
        }

        @Override
        public CompletableFuture<List<IslandLimitSnapshot>> getLimits(UUID islandId) {
            return client.environment().limits(islandId);
        }

        @Override
        public CompletableFuture<IslandLevelSnapshot> getLevel(UUID islandId) {
            return client.progression().level(islandId).thenApply(PaperCloudIslandsApi::level);
        }

        @Override
        public CompletableFuture<List<IslandRankSnapshot>> getTopByLevel(int limit) {
            return client.progression().topLevel(limit).thenApply(PaperCloudIslandsApi::rankings);
        }

        @Override
        public CompletableFuture<List<IslandRankSnapshot>> getTopByWorth(int limit) {
            return client.progression().topWorth(limit).thenApply(PaperCloudIslandsApi::rankings);
        }

        @Override
        public CompletableFuture<List<IslandReviewRankSnapshot>> getTopByReviews(int limit) {
            return client.progression().topReviews(limit).thenApply(PaperCloudIslandsApi::reviewRankings);
        }

        @Override
        public CompletableFuture<List<IslandReviewSnapshot>> getReviews(UUID islandId, int limit) {
            return client.navigation().listReviews(islandId, limit).thenApply(view -> reviews(islandId, view));
        }

        @Override
        public CompletableFuture<IslandVisitorStatsSnapshot> getVisitorStats(UUID islandId, int limit) {
            return client.visitorStats().stats(islandId, limit).thenApply(PaperCloudIslandsApi::visitorStats);
        }

        @Override
        public CompletableFuture<List<IslandWarehouseItemSnapshot>> getWarehouse(UUID islandId, int limit) {
            return client.warehouse().listItems(islandId, limit).thenApply(PaperCloudIslandsApi::warehouseItems);
        }

        @Override
        public CompletableFuture<List<IslandSnapshot>> getPublicIslands(int limit) {
            return client.navigation().publicIslands(limit).thenApply(PaperCloudIslandsApi::publicIslands);
        }

        @Override
        public CompletableFuture<List<IslandUpgradeSnapshot>> getUpgrades(UUID islandId) {
            return client.progression().upgrades(islandId).thenApply(views -> upgrades(islandId, views));
        }

        @Override
        public CompletableFuture<List<UpgradeRuleSnapshot>> getUpgradeRules() {
            return client.progression().upgradeRules().thenApply(PaperCloudIslandsApi::upgradeRules);
        }

        @Override
        public CompletableFuture<List<BlockValueSnapshot>> getBlockValues() {
            return client.blockValues().list().thenApply(PaperCloudIslandsApi::blockValues);
        }

        @Override
        public CompletableFuture<List<IslandMissionSnapshot>> getMissions(UUID islandId, String kind) {
            return client.progression().missions(islandId, kind).thenApply(views -> missions(islandId, kind, views));
        }

        @Override
        public CompletableFuture<List<IslandSnapshotRecord>> getSnapshots(UUID islandId, int limit) {
            return client.snapshots().records(islandId, limit);
        }

        @Override
        public CompletableFuture<List<IslandLogRecord>> getLogs(UUID islandId, int limit) {
            return client.communication().records(islandId, limit);
        }
        @Override
        public CompletableFuture<IslandBankSnapshot> getBank(UUID islandId) {
            return client.bank().snapshot(islandId);
        }
    }

    private static final class StatusService implements IslandStatusService {
        private final CloudIslandsPaperAgent agent;
        private final PaperRuntimeConfig config;

        private StatusService(CloudIslandsPaperAgent agent, PaperRuntimeConfig config) {
            this.agent = agent;
            this.config = config == null ? PaperRuntimeConfig.defaults() : config;
        }

        @Override
        public CompletableFuture<CloudIslandsStatusSnapshot> current() {
            org.bukkit.plugin.Plugin plugin = agent.plugin();
            kr.lunaf.cloudislands.paper.CloudIslandsPaperPlugin paperPlugin = (kr.lunaf.cloudislands.paper.CloudIslandsPaperPlugin) plugin;
            int activeIslands = paperPlugin.activeIslands() == null ? 0 : paperPlugin.activeIslands().size();
            int activationQueue = paperPlugin.jobWorker() == null ? 0 : paperPlugin.jobWorker().activationQueue();
            return CompletableFuture.completedFuture(new CloudIslandsStatusSnapshot(
                "paper",
                agent.role().name(),
                config.node().id(),
                plugin.getDescription().getVersion(),
                true,
                !resolved(config.coreApi().token()).isBlank() || envPresent("CI_CORE_TOKEN"),
                !resolved(config.coreApi().adminToken()).isBlank() || envPresent("CI_ADMIN_TOKEN"),
                config.security().requireVelocityForwarding(),
                !resolved(config.security().forwardingSecret()).isBlank() || envPresent("VELOCITY_FORWARDING_SECRET"),
                config.security().enforceRouteSession() || config.security().requireRouteSession(),
                plugin.getServer().getOnlinePlayers().size(),
                activeIslands,
                activationQueue,
                Instant.now(),
                kr.lunaf.cloudislands.api.CloudIslandsApiContract.READ_POLICY,
                kr.lunaf.cloudislands.api.CloudIslandsApiContract.WRITE_AUTHORITY,
                kr.lunaf.cloudislands.api.CloudIslandsApiContract.SYNC_EVENT_POLICY,
                kr.lunaf.cloudislands.api.CloudIslandsApiContract.ADDON_STORAGE_POLICY,
                kr.lunaf.cloudislands.api.CloudIslandsApiContract.JAVA_PLUGIN_API_POLICY,
                kr.lunaf.cloudislands.api.CloudIslandsApiContract.INTERNAL_API_POLICY,
                kr.lunaf.cloudislands.api.CloudIslandsApiContract.EVENT_API_POLICY,
                kr.lunaf.cloudislands.api.CloudIslandsApiContract.CORE_AUTH_POLICY,
                kr.lunaf.cloudislands.api.CloudIslandsApiContract.ADMIN_ENDPOINT_POLICY,
                kr.lunaf.cloudislands.api.CloudIslandsApiContract.NETWORK_EXPOSURE_POLICY,
                kr.lunaf.cloudislands.api.CloudIslandsApiContract.SECURITY_POSTURE_SUMMARY,
                kr.lunaf.cloudislands.api.CloudIslandsApiContract.TOPOLOGY_PRIVACY_POLICY,
                kr.lunaf.cloudislands.api.CloudIslandsApiContract.CONSISTENCY_AUTHORITY_POLICY,
                kr.lunaf.cloudislands.api.CloudIslandsApiContract.CONTRACT_VERSION
            ));
        }

        private static String resolved(String value) {
            if (value == null) {
                return "";
            }
            String trimmed = value.trim();
            if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
                return System.getenv().getOrDefault(trimmed.substring(2, trimmed.length() - 1), "");
            }
            return trimmed;
        }

        private static boolean envPresent(String name) {
            String value = System.getenv(name);
            return value != null && !value.isBlank();
        }
    }

    private static final class PlayerService implements PlayerIslandService {
        private final CoreApiClient client;
        private final QueryService query;

        private PlayerService(CoreApiClient client, QueryService query) {
            this.client = client;
            this.query = query;
        }

        @Override
        public CompletableFuture<Optional<UUID>> getOwnedIslandId(UUID playerUuid) {
            return query.getIslandByOwner(playerUuid).thenApply(island -> island.map(IslandSnapshot::islandId));
        }

        @Override
        public CompletableFuture<Boolean> hasIsland(UUID playerUuid) {
            return getOwnedIslandId(playerUuid).thenApply(Optional::isPresent);
        }

        @Override
        public CompletableFuture<List<IslandSnapshot>> getJoinedIslands(UUID playerUuid) {
            return CoreGuiViews.playerIslands(client, playerUuid).thenApply(PaperCloudIslandsApi::playerIslands);
        }

        @Override
        public CompletableFuture<Optional<PlayerIslandProfile>> getProfile(UUID playerUuid) {
            return client.playerProfiles().profile(playerUuid).thenApply(PaperCloudIslandsApi::playerProfile);
        }

        @Override
        public CompletableFuture<Optional<PlayerIslandProfile>> setPrimaryIsland(UUID playerUuid, UUID islandId) {
            return mutate("player.primary-island.set", () -> client.playerProfileCommands().setPrimaryIsland(playerUuid, islandId)).thenApply(PaperCloudIslandsApi::playerProfile);
        }

        @Override
        public CompletableFuture<Optional<PlayerIslandProfile>> clearPrimaryIsland(UUID playerUuid) {
            return mutate("player.primary-island.clear", () -> client.playerProfileCommands().clearPrimaryIsland(playerUuid)).thenApply(PaperCloudIslandsApi::playerProfile);
        }
    }

    private static final class RoutingService implements IslandRoutingService {
        private final CoreApiClient client;

        private RoutingService(CoreApiClient client) {
            this.client = client;
        }

        @Override public CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid) { return mutate("route.ticket.home", () -> client.createHomeTicket(playerUuid)); }
        @Override public CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid, String homeName) { return mutate("route.ticket.home.named", () -> client.createHomeTicket(playerUuid, homeName)); }
        @Override public CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID targetIslandId) { return mutate("route.ticket.visit", () -> client.createVisitTicket(visitorUuid, targetIslandId)); }
        @Override public CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, String islandName) { return mutate("route.ticket.visit.name", () -> client.createVisitTicket(visitorUuid, islandName)); }
        @Override public CompletableFuture<RouteTicket> createVisitTicketForOwner(UUID visitorUuid, UUID ownerUuid) { return mutate("route.ticket.visit.owner", () -> client.createVisitTicketForOwner(visitorUuid, ownerUuid)); }
        @Override public CompletableFuture<RouteTicket> createRandomVisitTicket(UUID visitorUuid) { return mutate("route.ticket.random-visit", () -> client.createRandomVisitTicket(visitorUuid)); }
        @Override public CompletableFuture<RouteTicket> createWarpTicket(UUID playerUuid, UUID islandId, String warpName) { return mutate("route.ticket.warp", () -> client.createWarpTicket(playerUuid, islandId, warpName)); }
        @Override public CompletableFuture<Void> publishRouteSession(RouteTicket ticket) { return publishRouteSessionResult(ticket).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> publishRouteSessionResult(RouteTicket ticket) { return mutate("route.session.publish", () -> client.routingCommands().publishRouteSessionResult(ticket)).thenApply(view -> action(view, "ROUTE_SESSION_PUBLISHED")); }
        @Override public CompletableFuture<Optional<PlayerRouteSessionSnapshot>> consumeRouteSession(UUID playerUuid, String nodeId) { return mutate("route.session.consume", () -> client.consumeRouteSession(playerUuid, nodeId)).thenApply(session -> session.map(PaperCloudIslandsApi::routeSession)); }
        @Override public CompletableFuture<Optional<RouteTicket>> routeTicketStatus(UUID ticketId, UUID playerUuid, String nonce) { return client.routeTicketStatus(ticketId, playerUuid, nonce); }
        @Override public CompletableFuture<Optional<RouteTicket>> consumeTicket(UUID ticketId, UUID playerUuid, String nodeId, String nonce) { return mutate("route.ticket.consume", () -> client.consumeTicket(ticketId, playerUuid, nodeId, nonce)); }
        @Override public CompletableFuture<RoutePlan> resolveHome(UUID playerUuid) { return createHomeTicket(playerUuid).thenApply(PaperCloudIslandsApi::plan); }
        @Override public CompletableFuture<RoutePlan> resolveHome(UUID playerUuid, String homeName) { return createHomeTicket(playerUuid, homeName).thenApply(PaperCloudIslandsApi::plan); }
        @Override public CompletableFuture<RoutePlan> resolveVisit(UUID visitorUuid, UUID targetIslandId) { return createVisitTicket(visitorUuid, targetIslandId).thenApply(PaperCloudIslandsApi::plan); }
        @Override public CompletableFuture<RoutePlan> resolveVisitByName(UUID visitorUuid, String islandName) { return createVisitTicket(visitorUuid, islandName).thenApply(PaperCloudIslandsApi::plan); }
        @Override public CompletableFuture<RoutePlan> resolveVisitByOwner(UUID visitorUuid, UUID ownerUuid) { return createVisitTicketForOwner(visitorUuid, ownerUuid).thenApply(PaperCloudIslandsApi::plan); }
        @Override public CompletableFuture<RoutePlan> resolveRandomVisit(UUID visitorUuid) { return createRandomVisitTicket(visitorUuid).thenApply(PaperCloudIslandsApi::plan); }
        @Override public CompletableFuture<RoutePlan> resolveWarp(UUID playerUuid, UUID islandId, String warpName) { return createWarpTicket(playerUuid, islandId, warpName).thenApply(PaperCloudIslandsApi::plan); }
    }

    private static final class PermissionService implements IslandPermissionService {
        private final CloudIslandsPaperAgent agent;

        private PermissionService(CloudIslandsPaperAgent agent) {
            this.agent = agent;
        }

        @Override
        public CompletableFuture<PermissionResult> check(UUID playerUuid, UUID islandId, IslandPermission permission) {
            boolean allowed = agent.permissionCache().allowed(islandId, playerUuid, permission, false);
            return CompletableFuture.completedFuture(allowed ? PermissionResult.allow(IslandRole.MEMBER) : PermissionResult.deny("DEFAULT_DENY", IslandRole.VISITOR));
        }

        @Override
        public CompletableFuture<PermissionResult> checkAt(UUID playerUuid, String worldName, int blockX, int blockY, int blockZ, IslandPermission permission) {
            return CompletableFuture.completedFuture(agent.protection().checkBlock(playerUuid, worldName, blockX, blockY, blockZ, permission));
        }
    }

    private static final class RuntimeService implements IslandRuntimeService {
        private final CoreApiClient client;

        private RuntimeService(CoreApiClient client) {
            this.client = client;
        }

        @Override
        public CompletableFuture<IslandRuntimeSnapshot> activate(UUID islandId, String preferredPool) {
            return mutate("runtime.island.activate", () -> client.lifecycle().activateIsland(islandId)).thenCompose(_body -> client.adminIslands().runtime(islandId)).thenApply(PaperCloudIslandsApi::runtime);
        }

        @Override
        public CompletableFuture<IslandActionResult> activateResult(UUID islandId, String preferredPool) {
            return mutate("runtime.island.activate", () -> client.lifecycle().activateIsland(islandId)).thenApply(view -> action(view, "ACTIVATED"));
        }

        @Override
        public CompletableFuture<Void> deactivate(UUID islandId) {
            return deactivateResult(islandId).thenApply(_result -> null);
        }

        @Override
        public CompletableFuture<IslandActionResult> deactivateResult(UUID islandId) {
            return mutate("runtime.island.deactivate", () -> client.lifecycle().deactivateIsland(islandId)).thenApply(view -> action(view, "DEACTIVATED"));
        }

        @Override
        public CompletableFuture<Void> heartbeat(String nodeId, NodeHeartbeat heartbeat) {
            return heartbeatResult(nodeId, heartbeat).thenApply(_result -> null);
        }

        @Override
        public CompletableFuture<IslandActionResult> heartbeatResult(String nodeId, NodeHeartbeat heartbeat) {
            return mutate("runtime.heartbeat.publish", () -> client.runtimeCommands().publishHeartbeat(new NodeHeartbeatRequest(
                NodeHeartbeatRequest.CURRENT_PROTOCOL_VERSION,
                nodeId,
                "island",
                nodeId,
                "paper-api",
                NodeState.READY,
                heartbeat.players(),
                90,
                110,
                20,
                heartbeat.activeIslands(),
                600,
                heartbeat.mspt(),
                heartbeat.activationQueue(),
                20,
                0.0D,
                heartbeat.heapUsedMb(),
                heartbeat.heapMaxMb(),
                0,
                true,
                "*"
            ))).thenApply(view -> action(view, "HEARTBEAT_ACCEPTED"));
        }

        @Override
        public CompletableFuture<Void> recordBlockDelta(UUID islandId, String materialKey, long delta) {
            return recordBlockDeltaResult(islandId, materialKey, delta).thenApply(_result -> null);
        }

        @Override
        public CompletableFuture<IslandActionResult> recordBlockDeltaResult(UUID islandId, String materialKey, long delta) {
            return mutate("runtime.block-delta.record", () -> client.runtimeCommands().recordBlockDelta(islandId, materialKey, delta)).thenApply(view -> action(view, "BLOCK_DELTA_RECORDED"));
        }

        @Override
        public CompletableFuture<List<ClaimedIslandJobSnapshot>> claimJobs(String nodeId, List<String> supportedTypes, int maxJobs) {
            return mutate("runtime.jobs.claim", () -> client.claimJobs(nodeId, jobTypes(supportedTypes), maxJobs)).thenApply(jobs -> jobs.stream().map(PaperCloudIslandsApi::claimedJob).toList());
        }

        @Override
        public CompletableFuture<List<ClaimedIslandJobSnapshot>> claimTypedJobs(String nodeId, List<IslandRuntimeJobType> supportedTypes, int maxJobs) {
            return mutate("runtime.jobs.claim", () -> client.claimJobs(nodeId, runtimeJobTypes(supportedTypes), maxJobs)).thenApply(jobs -> jobs.stream().map(PaperCloudIslandsApi::claimedJob).toList());
        }

        @Override
        public CompletableFuture<Void> completeJob(String nodeId, UUID jobId) {
            return completeJob(nodeId, jobId, Map.of());
        }

        @Override
        public CompletableFuture<Void> completeJob(String nodeId, UUID jobId, Map<String, String> payload) {
            return completeJobResult(nodeId, jobId, payload).thenApply(_result -> null);
        }

        @Override
        public CompletableFuture<IslandActionResult> completeJobResult(String nodeId, UUID jobId, Map<String, String> payload) {
            return mutateIdempotent("runtime.job.complete", () -> client.runtimeCommands().completeJob(nodeId, jobId, payload)).thenApply(view -> action(view, "JOB_COMPLETED"));
        }

        @Override
        public CompletableFuture<Void> failJob(String nodeId, UUID jobId, String errorMessage) {
            return failJobResult(nodeId, jobId, errorMessage).thenApply(_result -> null);
        }

        @Override
        public CompletableFuture<IslandActionResult> failJobResult(String nodeId, UUID jobId, String errorMessage) {
            return mutateIdempotent("runtime.job.fail", () -> client.runtimeCommands().failJob(nodeId, jobId, errorMessage)).thenApply(view -> action(view, "JOB_FAILED"));
        }
    }

    private static List<IslandJobType> jobTypes(List<String> supportedTypes) {
        if (supportedTypes == null || supportedTypes.isEmpty()) {
            return List.of(IslandJobType.CREATE_ISLAND, IslandJobType.ACTIVATE_ISLAND, IslandJobType.SAVE_ISLAND, IslandJobType.DEACTIVATE_ISLAND, IslandJobType.SNAPSHOT_ISLAND, IslandJobType.DELETE_ISLAND, IslandJobType.MIGRATE_ISLAND, IslandJobType.RESTORE_ISLAND, IslandJobType.RESET_ISLAND);
        }
        List<IslandJobType> types = new ArrayList<>();
        for (String supportedType : supportedTypes) {
            try {
                types.add(IslandJobType.valueOf(supportedType.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // Ignore unknown worker capabilities from external API callers.
            }
        }
        return types;
    }

    private static List<IslandJobType> runtimeJobTypes(List<IslandRuntimeJobType> supportedTypes) {
        if (supportedTypes == null || supportedTypes.isEmpty()) {
            return jobTypes(List.of());
        }
        return supportedTypes.stream().map(type -> IslandJobType.valueOf(type.name())).toList();
    }

    private static ClaimedIslandJobSnapshot claimedJob(IslandJob job) {
        return new ClaimedIslandJobSnapshot(
            job.jobId(),
            job.type().name(),
            job.islandId(),
            job.targetNode(),
            job.priority(),
            job.payload(),
            job.createdAt()
        );
    }

    private static final class AdminService implements IslandAdminService {
        private final CoreApiClient client;
        private final boolean superiorSkyblock2MigrationEnabled;

        private AdminService(CoreApiClient client, boolean superiorSkyblock2MigrationEnabled) {
            this.client = client;
            this.superiorSkyblock2MigrationEnabled = superiorSkyblock2MigrationEnabled;
        }

        @Override public CompletableFuture<Void> drainNode(String nodeId) { return drainNodeResult(nodeId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> drainNodeResult(String nodeId) { return mutate("admin.node.drain", () -> client.adminNodeCommands().drainNode(nodeId)).thenApply(view -> action(view, "NODE_DRAINED")); }
        @Override public CompletableFuture<Void> undrainNode(String nodeId) { return undrainNodeResult(nodeId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> undrainNodeResult(String nodeId) { return mutate("admin.node.undrain", () -> client.adminNodeCommands().undrainNode(nodeId)).thenApply(view -> action(view, "NODE_UNDRAINED")); }
        @Override public CompletableFuture<Void> sweepNode(String nodeId) { return sweepNodeResult(nodeId).thenApply(_result -> null); }
        @Override public CompletableFuture<NodeSweepResult> sweepNodeResult(String nodeId) { return mutate("admin.node.sweep", () -> client.adminNodeCommands().sweepNode(nodeId)).thenApply(PaperCloudIslandsApi::nodeSweep); }
        @Override public CompletableFuture<Void> kickAllNode(String nodeId, String reason) { return kickAllNodeResult(nodeId, reason).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> kickAllNodeResult(String nodeId, String reason) { return mutateIdempotent("admin.node.kickall", () -> client.adminNodeCommands().kickAllNode(nodeId, reason)).thenApply(view -> action(view, "NODE_KICKALL_REQUESTED")); }
        @Override public CompletableFuture<Void> shutdownNodeSafely(String nodeId, String reason) { return shutdownNodeSafelyResult(nodeId, reason).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> shutdownNodeSafelyResult(String nodeId, String reason) { return mutateIdempotent("admin.node.shutdown-safe", () -> client.adminNodeCommands().shutdownNodeSafely(nodeId, reason)).thenApply(view -> action(view, "NODE_SHUTDOWN_SAFE_REQUESTED")); }
        @Override public CompletableFuture<Void> activateIsland(UUID islandId) { return activateIslandResult(islandId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> activateIslandResult(UUID islandId) { return mutate("admin.island.activate", () -> client.lifecycle().activateIsland(islandId)).thenApply(view -> action(view, "ACTIVATE_REQUESTED")); }
        @Override public CompletableFuture<Void> deactivateIsland(UUID islandId) { return deactivateIslandResult(islandId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> deactivateIslandResult(UUID islandId) { return mutate("admin.island.deactivate", () -> client.lifecycle().deactivateIsland(islandId)).thenApply(view -> action(view, "DEACTIVATE_REQUESTED")); }
        @Override public CompletableFuture<Void> migrateIsland(UUID islandId, String targetNode) { return migrateIslandResult(islandId, targetNode).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> migrateIslandResult(UUID islandId, String targetNode) { return mutateIdempotent("admin.island.migrate", () -> client.lifecycle().migrateIsland(islandId, targetNode)).thenApply(view -> action(view, "MIGRATED")); }
        @Override public CompletableFuture<Void> saveIsland(UUID islandId) { return saveIslandResult(islandId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> saveIslandResult(UUID islandId) { return mutate("admin.island.save", () -> client.lifecycle().saveIsland(islandId, "ADMIN_SAVE")).thenApply(view -> action(view, "SAVE_REQUESTED")); }
        @Override public CompletableFuture<Void> snapshotIsland(UUID islandId, String reason) { return snapshotIslandResult(islandId, reason).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> snapshotIslandResult(UUID islandId, String reason) { return mutate("admin.island.snapshot", () -> client.lifecycle().snapshotIsland(islandId, reason)).thenApply(view -> action(view, "SNAPSHOT_REQUESTED")); }
        @Override public CompletableFuture<List<IslandSnapshotRecord>> listIslandSnapshots(UUID islandId, int limit) { return client.snapshots().records(islandId, limit); }
        @Override public CompletableFuture<Void> restoreIsland(UUID islandId, long snapshotNo) { return restoreIslandResult(islandId, snapshotNo).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> restoreIslandResult(UUID islandId, long snapshotNo) { return mutateIdempotent("admin.island.restore", () -> client.lifecycle().restoreIslandSnapshot(islandId, snapshotNo)).thenApply(view -> action(view, "RESTORE_REQUESTED")); }
        @Override public CompletableFuture<Void> rollbackIsland(UUID islandId, long snapshotNo) { return rollbackIslandResult(islandId, snapshotNo).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> rollbackIslandResult(UUID islandId, long snapshotNo) { return mutateIdempotent("admin.island.rollback", () -> client.lifecycle().rollbackIslandSnapshot(islandId, snapshotNo)).thenApply(view -> action(view, "ROLLBACK_REQUESTED")); }
        @Override public CompletableFuture<Void> quarantineIsland(UUID islandId, String reason) { return quarantineIslandResult(islandId, reason).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> quarantineIslandResult(UUID islandId, String reason) { return mutateIdempotent("admin.island.quarantine", () -> client.lifecycle().quarantineIsland(islandId, reason)).thenApply(view -> action(view, "QUARANTINED")); }
        @Override public CompletableFuture<Void> repairIsland(UUID islandId, String reason) { return repairIslandResult(islandId, reason).thenApply(_result -> null); }
        @Override public CompletableFuture<Optional<IslandRuntimeSnapshot>> repairIslandResult(UUID islandId, String reason) { return mutateIdempotent("admin.island.repair", () -> client.lifecycle().repairIsland(islandId, reason)).thenCompose(view -> view.accepted() ? client.adminIslands().runtime(islandId).thenApply(runtime -> Optional.of(PaperCloudIslandsApi.runtime(runtime))) : CompletableFuture.completedFuture(Optional.empty())); }
        @Override public CompletableFuture<Void> deleteIsland(UUID islandId) { return adminDeleteIslandResult(islandId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> adminDeleteIslandResult(UUID islandId) { return mutateIdempotent("admin.island.delete", () -> client.lifecycle().adminDeleteIsland(islandId)).thenApply(view -> action(view, "ISLAND_DELETED")); }
        @Override public CompletableFuture<RouteTicket> createAdminTeleportTicket(UUID playerUuid, UUID islandId) { return mutate("admin.route.teleport", () -> client.adminIslandTeleport(playerUuid, islandId)); }
        @Override public CompletableFuture<RoutePlan> resolveAdminTeleport(UUID playerUuid, UUID islandId) { return createAdminTeleportTicket(playerUuid, islandId).thenApply(PaperCloudIslandsApi::plan); }
        @Override public CompletableFuture<Optional<RouteTicket>> getRouteTicket(UUID ticketId) { return client.adminRoutes().ticket(ticketId).thenApply(ticket -> ticket.map(PaperCloudIslandsApi::routeTicket)); }
        @Override public CompletableFuture<Optional<PlayerRouteSessionSnapshot>> getRouteSession(UUID playerUuid) { return client.adminRoutes().debug(playerUuid).thenApply(PaperCloudIslandsApi::routeSession); }
        @Override public CompletableFuture<RouteDebugSnapshot> getRouteDebug() { return client.adminRoutes().debug(new UUID(0L, 0L)).thenApply(PaperCloudIslandsApi::routeDebug); }
        @Override public CompletableFuture<Void> clearRoute(UUID playerUuid, UUID ticketId) { return clearRouteResult(playerUuid, ticketId).thenApply(_result -> null); }
        @Override public CompletableFuture<RouteClearResult> clearRouteResult(UUID playerUuid, UUID ticketId) { return mutate("admin.route.clear", () -> client.adminRoutes().clear(playerUuid, ticketId)).thenApply(PaperCloudIslandsApi::routeClear); }
        @Override public CompletableFuture<List<IslandJobSnapshot>> listJobs() { return client.jobs().list().thenApply(PaperCloudIslandsApi::jobs); }
        @Override public CompletableFuture<Void> retryJob(UUID jobId) { return retryJobResult(jobId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> retryJobResult(UUID jobId) { return mutate("admin.job.retry", () -> client.jobCommands().retry(jobId)).thenApply(PaperCloudIslandsApi::action); }
        @Override public CompletableFuture<Void> cancelJob(UUID jobId) { return cancelJobResult(jobId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> cancelJobResult(UUID jobId) { return mutate("admin.job.cancel", () -> client.jobCommands().cancel(jobId)).thenApply(PaperCloudIslandsApi::action); }
        @Override public CompletableFuture<Void> recoverJobs(String nodeId, long minIdleMillis, int maxJobs) { return recoverJobsResult(nodeId, minIdleMillis, maxJobs).thenApply(_result -> null); }
        @Override public CompletableFuture<JobRecoveryResult> recoverJobsResult(String nodeId, long minIdleMillis, int maxJobs) { return mutate("admin.jobs.recover", () -> client.jobCommands().recover(nodeId, minIdleMillis, maxJobs)).thenApply(PaperCloudIslandsApi::jobRecovery); }
        @Override public CompletableFuture<Void> clearCache() { return clearCacheResult().thenApply(_result -> null); }
        @Override public CompletableFuture<CoreMaintenanceResult> clearCacheResult() { return mutate("admin.cache.clear", () -> client.adminMaintenance().clearCache()).thenApply(PaperCloudIslandsApi::maintenance); }
        @Override public CompletableFuture<Void> reload() { return reloadResult().thenApply(_result -> null); }
        @Override public CompletableFuture<CoreMaintenanceResult> reloadResult() { return mutate("admin.core.reload", () -> client.adminMaintenance().reload()).thenApply(PaperCloudIslandsApi::maintenance); }
        @Override public CompletableFuture<Optional<PlayerIslandProfile>> getPlayerProfile(UUID playerUuid) { return client.playerProfiles().profile(playerUuid).thenApply(PaperCloudIslandsApi::playerProfile); }
        @Override public CompletableFuture<Optional<PlayerIslandProfile>> setPlayerPrimaryIsland(UUID playerUuid, UUID islandId) { return mutate("admin.player.primary-island.set", () -> client.playerProfileCommands().setPrimaryIsland(playerUuid, islandId)).thenApply(PaperCloudIslandsApi::playerProfile); }
        @Override public CompletableFuture<Optional<PlayerIslandProfile>> clearPlayerPrimaryIsland(UUID playerUuid) { return mutate("admin.player.primary-island.clear", () -> client.playerProfileCommands().clearPrimaryIsland(playerUuid)).thenApply(PaperCloudIslandsApi::playerProfile); }
        @Override public CompletableFuture<Void> setBlockValue(UUID actorUuid, String materialKey, String worth, long levelPoints, long limit) { return setBlockValueResult(actorUuid, materialKey, worth, levelPoints, limit).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setBlockValueResult(UUID actorUuid, String materialKey, String worth, long levelPoints, long limit) { return mutate("admin.block-value.set", () -> client.blockValueCommands().set(actorUuid, materialKey, worth, levelPoints, limit)).thenApply(view -> action(view, "BLOCK_VALUE_SET")); }
        @Override public CompletableFuture<List<GlobalEventSnapshot>> listEvents() { return client.adminEvents().list(100).thenApply(PaperCloudIslandsApi::events); }
        @Override public CompletableFuture<List<GlobalEventSnapshot>> listEvents(int limit) { return client.adminEvents().list(limit).thenApply(PaperCloudIslandsApi::events); }
        @Override public CompletableFuture<GlobalEventBatchSnapshot> listEventBatch() { return client.adminEvents().list(100).thenApply(PaperCloudIslandsApi::eventBatch); }
        @Override public CompletableFuture<GlobalEventBatchSnapshot> listEventBatch(int limit) { return client.adminEvents().list(limit).thenApply(PaperCloudIslandsApi::eventBatch); }
        @Override public CompletableFuture<GlobalEventBatchSnapshot> listEventBatchSince(long sinceSeq, int limit) { return client.adminEvents().listSince(sinceSeq, limit).thenApply(PaperCloudIslandsApi::eventBatch); }
        @Override public CompletableFuture<List<AuditLogSnapshot>> listAuditLogs() { return client.adminAudit().list(100).thenApply(PaperCloudIslandsApi::auditLogs); }

        @Override
        public CompletableFuture<List<String>> listNodes() {
            return client.adminNodes().nodes().thenApply(PaperCloudIslandsApi::nodeIds);
        }

        @Override
        public CompletableFuture<List<IslandNodeSnapshot>> listNodeSnapshots() {
            return client.adminNodes().nodes();
        }

        @Override
        public CompletableFuture<Optional<IslandNodeSnapshot>> getNodeSnapshot(String nodeId) {
            return client.adminNodes().nodeSnapshot(nodeId);
        }

        @Override
        public CompletableFuture<List<IslandRuntimeSnapshot>> listNodeIslands(String nodeId, int limit) {
            return client.adminNodes().nodeIslandRuntimes(nodeId, limit).thenApply(PaperCloudIslandsApi::nodeIslands);
        }

        @Override
        public CompletableFuture<List<IslandTemplateSnapshot>> listTemplates() {
            return client.templates().list().thenApply(PaperCloudIslandsApi::templates);
        }

        @Override
        public CompletableFuture<IslandTemplateSnapshot> upsertTemplate(String templateId, String displayName, boolean enabled, String minNodeVersion) {
            return mutate("admin.template.upsert", () -> client.templateCommands().upsert(templateId, displayName, enabled, minNodeVersion)).thenApply(PaperCloudIslandsApi::template);
        }

        @Override
        public CompletableFuture<IslandTemplateSnapshot> enableTemplate(String templateId) {
            return mutate("admin.template.enable", () -> client.templateCommands().enable(templateId)).thenApply(PaperCloudIslandsApi::template);
        }

        @Override
        public CompletableFuture<IslandTemplateSnapshot> disableTemplate(String templateId) {
            return mutate("admin.template.disable", () -> client.templateCommands().disable(templateId)).thenApply(PaperCloudIslandsApi::template);
        }

        @Override
        public CompletableFuture<MigrationRunSnapshot> scanSuperiorSkyblock2(String path) {
            return migrateSuperiorSkyblock2("scan", path);
        }

        @Override
        public CompletableFuture<MigrationRunSnapshot> dryRunSuperiorSkyblock2(String path) {
            return migrateSuperiorSkyblock2("dryrun", path);
        }

        @Override
        public CompletableFuture<MigrationRunSnapshot> extractSuperiorSkyblock2(String outputPath) {
            return migrateSuperiorSkyblock2("extract", outputPath);
        }

        @Override
        public CompletableFuture<MigrationRunSnapshot> importSuperiorSkyblock2(String approvalToken) {
            return migrateSuperiorSkyblock2("import", approvalToken);
        }

        @Override
        public CompletableFuture<MigrationRunSnapshot> verifySuperiorSkyblock2(String path) {
            return migrateSuperiorSkyblock2("verify", path);
        }

        @Override
        public CompletableFuture<MigrationRunSnapshot> rollbackSuperiorSkyblock2(String path) {
            return migrateSuperiorSkyblock2("rollback", path);
        }

        private CompletableFuture<MigrationRunSnapshot> migrateSuperiorSkyblock2(String action, String path) {
            if (!superiorSkyblock2MigrationEnabled()) {
                return CompletableFuture.completedFuture(disabledMigration(path));
            }
            return mutateIdempotent("admin.migration.superiorskyblock2." + action, () -> client.migrations().migrateSuperiorSkyblock2(action, path));
        }

        private boolean superiorSkyblock2MigrationEnabled() {
            return superiorSkyblock2MigrationEnabled;
        }

        private MigrationRunSnapshot disabledMigration(String path) {
            return new MigrationRunSnapshot("DISABLED", path == null ? "" : path, 0, false, false, 0, false, 0, false, 0, List.of());
        }
    }

    private static final class EventService implements IslandEventService {
        private final CoreApiClient client;
        private final Plugin plugin;

        private EventService(CoreApiClient client, Plugin plugin) {
            this.client = client;
            this.plugin = plugin;
        }

        @Override
        public CompletableFuture<List<GlobalEventSnapshot>> listGlobalEvents() {
            return client.adminEvents().list(100).thenApply(PaperCloudIslandsApi::events);
        }

        @Override
        public CompletableFuture<List<GlobalEventSnapshot>> listGlobalEvents(int limit) {
            return client.adminEvents().list(limit).thenApply(PaperCloudIslandsApi::events);
        }

        @Override
        public CompletableFuture<List<GlobalEventSnapshot>> listGlobalEventsSince(long sinceSeq, int limit) {
            return client.adminEvents().listSince(sinceSeq, limit).thenApply(PaperCloudIslandsApi::events);
        }

        @Override
        public GlobalEventSubscription subscribeGlobalEvents(long sinceSeq, int limit, long intervalTicks, Consumer<List<GlobalEventSnapshot>> listener) {
            AtomicLong cursor = new AtomicLong(Math.max(0L, sinceSeq));
            int safeLimit = Math.max(1, limit);
            long safeInterval = Math.max(1L, intervalTicks);
            BukkitTask task = kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.runTimerAsync(plugin, () ->
                listGlobalEventsSince(cursor.get(), safeLimit)
                    .thenAccept(events -> {
                        if (events.isEmpty()) {
                            return;
                        }
                        long latest = events.stream().mapToLong(GlobalEventSnapshot::sequence).max().orElse(cursor.get());
                        cursor.set(Math.max(cursor.get(), latest));
                        listener.accept(events);
                    })
                    .exceptionally(exception -> null), 1L, safeInterval);
            return task::cancel;
        }

        @Override
        public CompletableFuture<GlobalEventBatchSnapshot> listGlobalEventBatch() {
            return client.adminEvents().list(100).thenApply(PaperCloudIslandsApi::eventBatch);
        }

        @Override
        public CompletableFuture<GlobalEventBatchSnapshot> listGlobalEventBatch(int limit) {
            return client.adminEvents().list(limit).thenApply(PaperCloudIslandsApi::eventBatch);
        }

        @Override
        public CompletableFuture<GlobalEventBatchSnapshot> listGlobalEventBatchSince(long sinceSeq, int limit) {
            return client.adminEvents().listSince(sinceSeq, limit).thenApply(PaperCloudIslandsApi::eventBatch);
        }
    }

    private static final class CommandService implements IslandCommandService {
        private final CoreApiClient client;

        private CommandService(CoreApiClient client) {
            this.client = client;
        }

        @Override public CompletableFuture<CreateIslandResult> createIsland(UUID ownerUuid) { return createIsland(ownerUuid, "default"); }
        @Override public CompletableFuture<CreateIslandResult> createIsland(UUID ownerUuid, String templateId) { return mutate("island.create", () -> client.createIsland(ownerUuid, templateId)); }
        @Override public CompletableFuture<DeleteIslandResult> deleteIsland(UUID requesterUuid, UUID islandId) { return mutateIdempotent("island.delete", () -> client.deleteIsland(requesterUuid, islandId)); }
        @Override public CompletableFuture<Void> resetIsland(UUID islandId, UUID actorUuid, String reason) { return resetIslandResult(islandId, actorUuid, reason).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> resetIslandResult(UUID islandId, UUID actorUuid, String reason) { return mutateIdempotent("island.reset", () -> client.lifecycle().resetIsland(islandId, actorUuid, reason)).thenApply(view -> action(view, "RESET_QUEUED")); }
        @Override public CompletableFuture<Void> invite(UUID islandId, UUID inviterUuid, UUID targetUuid) { return inviteResult(islandId, inviterUuid, targetUuid).thenApply(_invite -> null); }
        @Override public CompletableFuture<IslandInviteSnapshot> inviteResult(UUID islandId, UUID inviterUuid, UUID targetUuid) { return mutate("island.invite.create", () -> client.memberCommands().createInvite(islandId, inviterUuid, targetUuid)).thenApply(PaperCloudIslandsApi::invite); }
        @Override public CompletableFuture<Void> acceptInvite(UUID inviteId, UUID playerUuid) { return acceptInviteResult(inviteId, playerUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandInviteActionResult> acceptInviteResult(UUID inviteId, UUID playerUuid) { return mutate("island.invite.accept", () -> client.memberCommands().acceptInvite(inviteId, playerUuid)); }
        @Override public CompletableFuture<Void> declineInvite(UUID inviteId, UUID playerUuid) { return declineInviteResult(inviteId, playerUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandInviteActionResult> declineInviteResult(UUID inviteId, UUID playerUuid) { return mutate("island.invite.decline", () -> client.memberCommands().declineInvite(inviteId, playerUuid)); }
        @Override public CompletableFuture<Void> acceptInviteFromIsland(UUID playerUuid, UUID islandId) { return acceptInviteFromIslandResult(playerUuid, islandId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandInviteActionResult> acceptInviteFromIslandResult(UUID playerUuid, UUID islandId) { return pendingInvite(playerUuid, invite -> invite.islandId().equals(islandId)).thenCompose(invite -> invite.map(value -> acceptInviteResult(value.inviteId(), playerUuid)).orElseGet(() -> CompletableFuture.completedFuture(new IslandInviteActionResult(false, "INVITE_UNAVAILABLE")))); }
        @Override public CompletableFuture<Void> declineInviteFromIsland(UUID playerUuid, UUID islandId) { return declineInviteFromIslandResult(playerUuid, islandId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandInviteActionResult> declineInviteFromIslandResult(UUID playerUuid, UUID islandId) { return pendingInvite(playerUuid, invite -> invite.islandId().equals(islandId)).thenCompose(invite -> invite.map(value -> declineInviteResult(value.inviteId(), playerUuid)).orElseGet(() -> CompletableFuture.completedFuture(new IslandInviteActionResult(false, "INVITE_UNAVAILABLE")))); }
        @Override public CompletableFuture<Void> acceptInviteFromPlayer(UUID playerUuid, UUID inviterUuid) { return acceptInviteFromPlayerResult(playerUuid, inviterUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandInviteActionResult> acceptInviteFromPlayerResult(UUID playerUuid, UUID inviterUuid) { return pendingInvite(playerUuid, invite -> invite.inviterUuid().equals(inviterUuid)).thenCompose(invite -> invite.map(value -> acceptInviteResult(value.inviteId(), playerUuid)).orElseGet(() -> CompletableFuture.completedFuture(new IslandInviteActionResult(false, "INVITE_UNAVAILABLE")))); }
        @Override public CompletableFuture<Void> declineInviteFromPlayer(UUID playerUuid, UUID inviterUuid) { return declineInviteFromPlayerResult(playerUuid, inviterUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandInviteActionResult> declineInviteFromPlayerResult(UUID playerUuid, UUID inviterUuid) { return pendingInvite(playerUuid, invite -> invite.inviterUuid().equals(inviterUuid)).thenCompose(invite -> invite.map(value -> declineInviteResult(value.inviteId(), playerUuid)).orElseGet(() -> CompletableFuture.completedFuture(new IslandInviteActionResult(false, "INVITE_UNAVAILABLE")))); }
        @Override public CompletableFuture<Void> banVisitor(UUID islandId, UUID actorUuid, UUID targetUuid, String reason) { return banVisitorResult(islandId, actorUuid, targetUuid, reason).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> banVisitorResult(UUID islandId, UUID actorUuid, UUID targetUuid, String reason) { return mutateIdempotent("island.visitor.ban", () -> client.memberCommands().banVisitor(islandId, actorUuid, targetUuid, reason)).thenApply(view -> action(view, "VISITOR_BANNED")); }
        @Override public CompletableFuture<Void> pardonVisitor(UUID islandId, UUID actorUuid, UUID targetUuid) { return pardonVisitorResult(islandId, actorUuid, targetUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> pardonVisitorResult(UUID islandId, UUID actorUuid, UUID targetUuid) { return mutateIdempotent("island.visitor.pardon", () -> client.memberCommands().pardonVisitor(islandId, actorUuid, targetUuid)).thenApply(view -> action(view, "VISITOR_PARDONED")); }
        @Override public CompletableFuture<Void> kick(UUID islandId, UUID actorUuid, UUID targetUuid) { return kickResult(islandId, actorUuid, targetUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> kickResult(UUID islandId, UUID actorUuid, UUID targetUuid) { return mutateIdempotent("island.member.remove", () -> client.memberCommands().removeMember(islandId, actorUuid, targetUuid)).thenApply(view -> action(view, "MEMBER_REMOVED")); }
        @Override public CompletableFuture<Void> trustPlayer(UUID islandId, UUID actorUuid, UUID targetUuid) { return trustPlayerResult(islandId, actorUuid, targetUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> trustPlayerResult(UUID islandId, UUID actorUuid, UUID targetUuid) { return mutate("island.member.trust", () -> client.memberCommands().setRole(islandId, actorUuid, targetUuid, "TRUSTED")).thenApply(view -> action(view, "PLAYER_TRUSTED")); }
        @Override public CompletableFuture<Void> untrustPlayer(UUID islandId, UUID actorUuid, UUID targetUuid) { return untrustPlayerResult(islandId, actorUuid, targetUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> untrustPlayerResult(UUID islandId, UUID actorUuid, UUID targetUuid) { return mutate("island.member.untrust", () -> client.memberCommands().setRole(islandId, actorUuid, targetUuid, "MEMBER")).thenApply(view -> action(view, "PLAYER_UNTRUSTED")); }
        @Override public CompletableFuture<IslandActionResult> setRoleResult(UUID islandId, UUID actorUuid, UUID targetUuid, String roleKey) { return mutate("island.member.role.set", () -> client.memberCommands().setRole(islandId, actorUuid, targetUuid, roleKey)).thenApply(view -> action(view, "MEMBER_ROLE_SET")); }
        @Override public CompletableFuture<Void> transferOwnership(UUID islandId, UUID actorUuid, UUID targetUuid) { return transferOwnershipResult(islandId, actorUuid, targetUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> transferOwnershipResult(UUID islandId, UUID actorUuid, UUID targetUuid) { return mutateIdempotent("island.ownership.transfer", () -> client.memberCommands().transferOwnership(islandId, actorUuid, targetUuid)).thenApply(view -> action(view, "OWNERSHIP_TRANSFERRED")); }
        @Override public CompletableFuture<Void> setFlag(UUID islandId, UUID actorUuid, IslandFlag flag, String value) { return setFlagResult(islandId, actorUuid, flag, value).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setFlagResult(UUID islandId, UUID actorUuid, IslandFlag flag, String value) { return mutate("island.flag.set", () -> client.environmentCommands().setFlag(islandId, actorUuid, flag, value)).thenApply(view -> action(view, "FLAG_SET")); }
        @Override public CompletableFuture<IslandActionResult> setPermissionResult(UUID islandId, UUID actorUuid, String roleKey, IslandPermission permission, boolean allowed) { return mutate("island.permission.set", () -> client.permissions().setPermission(islandId, actorUuid, roleKey, permission, allowed)).thenApply(view -> action(view, "PERMISSION_SET")); }
        @Override public CompletableFuture<IslandRoleSnapshot> upsertRoleResult(UUID islandId, UUID actorUuid, String roleKey, int weight, String displayName) { return mutate("island.role.upsert", () -> client.permissions().upsertRole(islandId, actorUuid, roleKey, weight, displayName)).thenApply(result -> role(islandId, result.value())); }
        @Override public CompletableFuture<IslandActionResult> resetRoleResult(UUID islandId, UUID actorUuid, String roleKey) { return mutateIdempotent("island.role.reset", () -> client.permissions().resetRole(islandId, actorUuid, roleKey)).thenApply(PaperCloudIslandsApi::roleResetAction); }
        @Override public CompletableFuture<Void> setLocked(UUID islandId, UUID actorUuid, boolean locked) { return setLockedResult(islandId, actorUuid, locked).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setLockedResult(UUID islandId, UUID actorUuid, boolean locked) { return mutate("island.locked.set", () -> client.settingsCommands().setLocked(islandId, actorUuid, locked)).thenApply(view -> action(view, locked ? "ISLAND_LOCKED" : "ISLAND_UNLOCKED")); }
        @Override public CompletableFuture<Void> lockIsland(UUID islandId, UUID actorUuid) { return lockIslandResult(islandId, actorUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> lockIslandResult(UUID islandId, UUID actorUuid) { return setLockedResult(islandId, actorUuid, true); }
        @Override public CompletableFuture<Void> unlockIsland(UUID islandId, UUID actorUuid) { return unlockIslandResult(islandId, actorUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> unlockIslandResult(UUID islandId, UUID actorUuid) { return setLockedResult(islandId, actorUuid, false); }
        @Override public CompletableFuture<Void> setHome(UUID islandId, UUID actorUuid, IslandLocation location) { return setHomeResult(islandId, actorUuid, location).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setHomeResult(UUID islandId, UUID actorUuid, IslandLocation location) { return setHomeResult(islandId, actorUuid, "default", location); }
        @Override public CompletableFuture<Void> setHome(UUID islandId, UUID actorUuid, String name, IslandLocation location) { return setHomeResult(islandId, actorUuid, name, location).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setHomeResult(UUID islandId, UUID actorUuid, String name, IslandLocation location) { return mutate("island.home.set", () -> client.homeWarpCommands().setHome(islandId, actorUuid, name, location)).thenApply(view -> action(view, "HOME_SET")); }
        @Override public CompletableFuture<Void> setBiome(UUID islandId, UUID actorUuid, String biomeKey) { return setBiomeResult(islandId, actorUuid, biomeKey).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setBiomeResult(UUID islandId, UUID actorUuid, String biomeKey) { return mutate("island.biome.set", () -> client.environmentCommands().setBiome(islandId, actorUuid, biomeKey)).thenApply(view -> action(view, "BIOME_SET")); }
        @Override public CompletableFuture<Void> setLimit(UUID islandId, UUID actorUuid, String limitKey, long value) { return setLimitResult(islandId, actorUuid, limitKey, value).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandLimitSnapshot> setLimitResult(UUID islandId, UUID actorUuid, String limitKey, long value) { return mutate("island.limit.set", () -> client.environmentCommands().setLimit(islandId, actorUuid, limitKey, value)).thenApply(view -> limit(islandId, actorUuid, view)); }
        @Override public CompletableFuture<Void> createWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location) { return createWarpResult(islandId, actorUuid, name, location).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> createWarpResult(UUID islandId, UUID actorUuid, String name, IslandLocation location) { return setWarpResult(islandId, actorUuid, name, location, false); }
        @Override public CompletableFuture<Void> setWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess) { return setWarpResult(islandId, actorUuid, name, location, publicAccess).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setWarpResult(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess) { return mutate("island.warp.set", () -> client.homeWarpCommands().setWarp(islandId, actorUuid, name, location, publicAccess)).thenApply(view -> action(view, "WARP_SET")); }
        @Override public CompletableFuture<Void> deleteWarp(UUID islandId, UUID actorUuid, String name) { return deleteWarpResult(islandId, actorUuid, name).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> deleteWarpResult(UUID islandId, UUID actorUuid, String name) { return mutateIdempotent("island.warp.delete", () -> client.homeWarpCommands().deleteWarp(islandId, actorUuid, name)).thenApply(view -> action(view, "WARP_DELETED")); }
        @Override public CompletableFuture<Void> setWarpPublicAccess(UUID islandId, UUID actorUuid, String name, boolean publicAccess) { return setWarpPublicAccessResult(islandId, actorUuid, name, publicAccess).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setWarpPublicAccessResult(UUID islandId, UUID actorUuid, String name, boolean publicAccess) { return mutate("island.warp.public-access.set", () -> client.homeWarpCommands().setWarpPublicAccess(islandId, actorUuid, name, publicAccess)).thenApply(view -> action(view, publicAccess ? "WARP_PUBLIC_ACCESS_ENABLED" : "WARP_PUBLIC_ACCESS_DISABLED")); }
        @Override public CompletableFuture<Void> publishWarp(UUID islandId, UUID actorUuid, String name) { return publishWarpResult(islandId, actorUuid, name).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> publishWarpResult(UUID islandId, UUID actorUuid, String name) { return setWarpPublicAccessResult(islandId, actorUuid, name, true); }
        @Override public CompletableFuture<Void> privatizeWarp(UUID islandId, UUID actorUuid, String name) { return privatizeWarpResult(islandId, actorUuid, name).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> privatizeWarpResult(UUID islandId, UUID actorUuid, String name) { return setWarpPublicAccessResult(islandId, actorUuid, name, false); }
        @Override public CompletableFuture<Void> setPublicAccess(UUID islandId, UUID actorUuid, boolean publicAccess) { return setPublicAccessResult(islandId, actorUuid, publicAccess).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setPublicAccessResult(UUID islandId, UUID actorUuid, boolean publicAccess) { return mutate("island.public-access.set", () -> client.settingsCommands().setPublicAccess(islandId, actorUuid, publicAccess)).thenApply(view -> action(view, publicAccess ? "PUBLIC_ACCESS_ENABLED" : "PUBLIC_ACCESS_DISABLED")); }
        @Override public CompletableFuture<Void> publishIsland(UUID islandId, UUID actorUuid) { return publishIslandResult(islandId, actorUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> publishIslandResult(UUID islandId, UUID actorUuid) { return setPublicAccessResult(islandId, actorUuid, true); }
        @Override public CompletableFuture<Void> privatizeIsland(UUID islandId, UUID actorUuid) { return privatizeIslandResult(islandId, actorUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> privatizeIslandResult(UUID islandId, UUID actorUuid) { return setPublicAccessResult(islandId, actorUuid, false); }
        @Override public CompletableFuture<IslandLevelSnapshot> recalculateLevel(UUID islandId, UUID actorUuid) { return mutate("island.level.recalculate", () -> client.progressionCommands().recalculateLevel(islandId, actorUuid)).thenApply(PaperCloudIslandsApi::level); }
        @Override public CompletableFuture<Void> purchaseUpgrade(UUID islandId, UUID actorUuid, String upgradeKey) { return purchaseUpgradeResult(islandId, actorUuid, upgradeKey).thenApply(_result -> null); }
        @Override public CompletableFuture<UpgradePurchaseSnapshot> purchaseUpgradeResult(UUID islandId, UUID actorUuid, String upgradeKey) { return mutateIdempotent("island.upgrade.purchase", () -> client.progressionCommands().purchaseUpgrade(islandId, actorUuid, upgradeKey)).thenApply(PaperCloudIslandsApi::upgradePurchase); }
        @Override public CompletableFuture<Void> completeMission(UUID islandId, UUID actorUuid, String missionKey) { return completeMission(islandId, actorUuid, missionKey, "MISSION"); }
        @Override public CompletableFuture<Optional<IslandMissionSnapshot>> completeMissionResult(UUID islandId, UUID actorUuid, String missionKey) { return completeMissionResult(islandId, actorUuid, missionKey, "MISSION"); }
        @Override public CompletableFuture<Void> completeMission(UUID islandId, UUID actorUuid, String missionKey, String kind) { return completeMissionResult(islandId, actorUuid, missionKey, kind).thenApply(_result -> null); }
        @Override public CompletableFuture<Optional<IslandMissionSnapshot>> completeMissionResult(UUID islandId, UUID actorUuid, String missionKey, String kind) { return mutateIdempotent("island.mission.complete", () -> client.progressionCommands().completeMission(islandId, actorUuid, missionKey, kind)).thenApply(PaperCloudIslandsApi::mission); }
        @Override public CompletableFuture<Optional<IslandMissionSnapshot>> progressMissionResult(UUID islandId, UUID actorUuid, String missionKey, String kind, long amount) { return mutateIdempotent("island.mission.progress", () -> client.progressionCommands().progressMission(islandId, actorUuid, missionKey, kind, amount)).thenApply(PaperCloudIslandsApi::mission); }
        @Override public CompletableFuture<List<MissionProviderDefinitionSnapshot>> registerMissionProvider(String providerId, List<MissionProviderDefinitionSnapshot> definitions) { return mutate("island.mission-provider.register", () -> client.progressionCommands().registerMissionProvider(providerId, definitions)); }
        @Override public CompletableFuture<Void> sendChat(UUID islandId, UUID actorUuid, String channel, String message) { return sendChatResult(islandId, actorUuid, channel, message).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandChatResult> sendChatResult(UUID islandId, UUID actorUuid, String channel, String message) { return mutate("island.chat.send", () -> client.communicationCommands().sendChat(islandId, actorUuid, channel, message)).thenApply(PaperCloudIslandsApi::chatResult); }
        @Override public CompletableFuture<Void> sendIslandChat(UUID islandId, UUID actorUuid, String message) { return sendIslandChatResult(islandId, actorUuid, message).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandChatResult> sendIslandChatResult(UUID islandId, UUID actorUuid, String message) { return sendChatResult(islandId, actorUuid, "ISLAND", message); }
        @Override public CompletableFuture<Void> sendTeamChat(UUID islandId, UUID actorUuid, String message) { return sendTeamChatResult(islandId, actorUuid, message).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandChatResult> sendTeamChatResult(UUID islandId, UUID actorUuid, String message) { return sendChatResult(islandId, actorUuid, "TEAM", message); }
        @Override public CompletableFuture<Void> depositBank(UUID islandId, UUID actorUuid, BigDecimal amount) { return depositBankResult(islandId, actorUuid, amount).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandBankChangeSnapshot> depositBankResult(UUID islandId, UUID actorUuid, BigDecimal amount) { return mutateIdempotent("island.bank.deposit", () -> client.bankCommands().depositSnapshot(islandId, actorUuid, amount.toPlainString())).thenApply(view -> bankChange(view, "DEPOSITED")); }
        @Override public CompletableFuture<Void> withdrawBank(UUID islandId, UUID actorUuid, BigDecimal amount) { return withdrawBankResult(islandId, actorUuid, amount).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandBankChangeSnapshot> withdrawBankResult(UUID islandId, UUID actorUuid, BigDecimal amount) { return mutateIdempotent("island.bank.withdraw", () -> client.bankCommands().withdrawSnapshot(islandId, actorUuid, amount.toPlainString())).thenApply(PaperCloudIslandsApi::bankChange); }

        private CompletableFuture<Optional<IslandInviteSnapshot>> pendingInvite(UUID playerUuid, java.util.function.Predicate<IslandInviteSnapshot> predicate) {
            return client.members().inviteSnapshots(playerUuid)
                .thenApply(invites -> invites.stream().filter(predicate).findFirst());
        }
    }

    private static Optional<IslandSnapshot> island(CoreGuiViews.IslandInfoView view) {
        if (view == null || view.islandId() == null || view.islandId().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new IslandSnapshot(
            uuidValueOrZero(view.islandId()),
            uuidValueOrZero(view.ownerUuid()),
            view.name() == null ? "" : view.name(),
            enumValue(IslandState.class, view.state() == null || view.state().isBlank() ? "INACTIVE_READY" : view.state(), IslandState.INACTIVE_READY),
            intValue(view.size()),
            view.level(),
            view.worth() == null || view.worth().isBlank() ? "0" : view.worth(),
            view.publicAccess(),
            instant(view.createdAt()),
            instant(view.updatedAt())
        ));
    }

    private static List<IslandSnapshot> playerIslands(List<CoreGuiViews.PlayerIslandView> views) {
        return views.stream()
            .map(PaperCloudIslandsApi::playerIsland)
            .flatMap(Optional::stream)
            .toList();
    }

    private static Optional<IslandSnapshot> playerIsland(CoreGuiViews.PlayerIslandView view) {
        if (view == null || view.islandId() == null || view.islandId().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new IslandSnapshot(
            uuidValueOrZero(view.islandId()),
            new UUID(0L, 0L),
            view.name() == null || view.name().isBlank() ? view.islandId() : view.name(),
            enumValue(IslandState.class, view.state() == null || view.state().isBlank() ? "INACTIVE_READY" : view.state(), IslandState.INACTIVE_READY),
            0,
            view.level(),
            view.worth() == null || view.worth().isBlank() ? "0" : view.worth(),
            false,
            Instant.EPOCH,
            Instant.EPOCH
        ));
    }

    private static List<IslandSnapshot> publicIslands(List<CoreGuiViews.PublicIslandView> views) {
        return views.stream()
            .map(PaperCloudIslandsApi::publicIsland)
            .flatMap(Optional::stream)
            .toList();
    }

    private static Optional<IslandSnapshot> publicIsland(CoreGuiViews.PublicIslandView view) {
        if (view == null || view.islandId() == null || view.islandId().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new IslandSnapshot(
            uuidValueOrZero(view.islandId()),
            uuidValueOrZero(view.ownerUuid()),
            view.name() == null || view.name().isBlank() ? view.islandId() : view.name(),
            IslandState.ACTIVE,
            0,
            view.level(),
            view.worth() == null || view.worth().isBlank() ? "0" : view.worth(),
            true,
            Instant.EPOCH,
            Instant.EPOCH
        ));
    }

    private static IslandRegionSnapshot region(IslandRegion region) {
        return new IslandRegionSnapshot(
            region.islandId(),
            region.world(),
            region.minX(),
            region.maxX(),
            region.minZ(),
            region.maxZ(),
            region.cellX(),
            region.cellZ(),
            region.originX(),
            region.originZ()
        );
    }

    private static IslandBoundarySnapshot boundary(CoreGuiViews.IslandInfoView view) {
        int size = intValue(view == null ? 0L : view.size());
        return new IslandBoundarySnapshot(view == null ? new UUID(0L, 0L) : uuidValueOrZero(view.islandId()), size, intValue(view == null ? size : view.border()));
    }

    private static IslandSizeSnapshot size(CoreGuiViews.IslandInfoView view) {
        int size = intValue(view == null ? 0L : view.size());
        return new IslandSizeSnapshot(view == null ? new UUID(0L, 0L) : uuidValueOrZero(view.islandId()), size, intValue(view == null ? size : view.border()));
    }

    private static IslandWorthSnapshot worth(CoreGuiViews.IslandInfoView view) {
        return new IslandWorthSnapshot(view == null ? new UUID(0L, 0L) : uuidValueOrZero(view.islandId()), view == null || view.worth() == null || view.worth().isBlank() ? "0" : view.worth());
    }

    private static Optional<PlayerIslandProfile> playerProfile(PlayerProfileView profile) {
        UUID playerUuid = uuidValue(profile.playerUuid());
        if (playerUuid == null) {
            return Optional.empty();
        }
        UUID primaryIslandId = uuidValue(profile.primaryIslandId());
        return Optional.of(new PlayerIslandProfile(
            playerUuid,
            profile.lastName(),
            Optional.ofNullable(primaryIslandId),
            instant(profile.lastSeenAt().isBlank() ? Instant.EPOCH.toString() : profile.lastSeenAt()),
            profile.locale()
        ));
    }

    private static PlayerRouteSessionSnapshot routeSession(PlayerRouteSession session) {
        return new PlayerRouteSessionSnapshot(
            session.playerUuid(),
            session.ticketId(),
            session.targetNode(),
            session.targetServerName(),
            session.nonce(),
            session.expiresAt()
        );
    }

    private static PlayerRouteSessionSnapshot routeSession(AdminRouteSessionView session) {
        return new PlayerRouteSessionSnapshot(
            uuidValueOrZero(session.playerUuid()),
            uuidValueOrZero(session.ticketId()),
            session.targetNode(),
            session.targetServerName(),
            session.nonce(),
            instant(session.expiresAt().isBlank() ? Instant.EPOCH.toString() : session.expiresAt())
        );
    }

    private static Optional<PlayerRouteSessionSnapshot> routeSession(AdminRouteDebugView debug) {
        if (debug == null || debug.sessions().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(routeSession(debug.sessions().get(0)));
    }

    private static RouteDebugSnapshot routeDebug(AdminRouteDebugView debug) {
        if (debug == null) {
            return new RouteDebugSnapshot(List.of(), List.of());
        }
        return new RouteDebugSnapshot(
            debug.sessions().stream().map(PaperCloudIslandsApi::routeSession).toList(),
            debug.tickets().stream().map(PaperCloudIslandsApi::routeTicket).toList()
        );
    }

    private static IslandRuntimeSnapshot runtime(AdminIslandRuntimeView view) {
        if (view == null) {
            return new IslandRuntimeSnapshot(new UUID(0L, 0L), IslandState.RECOVERY_REQUIRED, null, null, null, null, null, 0L, null, null);
        }
        return new IslandRuntimeSnapshot(
            uuidValueOrZero(view.islandId()),
            enumValue(IslandState.class, view.state() == null || view.state().isBlank() ? "RECOVERY_REQUIRED" : view.state(), IslandState.RECOVERY_REQUIRED),
            nullableBlank(view.activeNode()),
            nullableBlank(view.activeWorld()),
            nullableInt(view.cellX()),
            nullableInt(view.cellZ()),
            nullableBlank(view.leaseOwner()),
            view.fencingToken(),
            nullableInstantValue(view.activatedAt()),
            nullableInstantValue(view.lastHeartbeat())
        );
    }

    private static List<IslandRuntimeSnapshot> nodeIslands(List<AdminIslandRuntimeView> views) {
        return (views == null ? List.<AdminIslandRuntimeView>of() : views).stream()
            .map(PaperCloudIslandsApi::runtime)
            .toList();
    }

    private static RoutePlan plan(RouteTicket ticket) {
        return new RoutePlan(ticket.islandId(), ticket.targetNode(), ticket.payload().getOrDefault("targetServerName", ticket.targetNode()), ticket.action(), ticket.state() == RouteTicketState.PREPARING);
    }

    private static IslandInviteSnapshot invite(CoreGuiViews.InviteView view) {
        return new IslandInviteSnapshot(
            view == null ? new UUID(0L, 0L) : uuidValueOrZero(view.inviteId()),
            view == null ? new UUID(0L, 0L) : uuidValueOrZero(view.islandId()),
            view == null ? new UUID(0L, 0L) : uuidValueOrZero(view.inviterUuid()),
            view == null ? new UUID(0L, 0L) : uuidValueOrZero(view.targetUuid()),
            view == null ? "PENDING" : view.state(),
            instant(view == null ? "" : view.createdAt()),
            instant(view == null ? "" : view.expiresAt())
        );
    }

    private static IslandActionResult action(String json, String successCode) {
        boolean accepted = json.contains("\"accepted\":true");
        return new IslandActionResult(accepted, accepted ? successCode : text(json, "code", "FAILED"));
    }

    private static IslandActionResult action(JobActionView view) {
        if (view == null) {
            return new IslandActionResult(false, "FAILED");
        }
        return new IslandActionResult(view.accepted(), view.code().isBlank() ? (view.accepted() ? "ACCEPTED" : "FAILED") : view.code());
    }

    private static IslandActionResult action(AdminNodeActionView view, String fallbackCode) {
        if (view == null) {
            return new IslandActionResult(false, "FAILED");
        }
        return new IslandActionResult(view.accepted(), view.code().isBlank() ? (view.accepted() ? fallbackCode : "FAILED") : view.code());
    }

    private static IslandActionResult action(IslandLifecycleActionView view, String fallbackCode) {
        if (view == null) {
            return new IslandActionResult(false, "FAILED");
        }
        return new IslandActionResult(view.accepted(), view.code().isBlank() ? (view.accepted() ? fallbackCode : "FAILED") : view.code());
    }

    private static IslandActionResult action(RuntimeActionView view, String fallbackCode) {
        if (view == null) {
            return new IslandActionResult(false, "FAILED");
        }
        return new IslandActionResult(view.accepted(), view.code().isBlank() ? (view.accepted() ? fallbackCode : "FAILED") : view.code());
    }

    private static IslandActionResult action(RoutePublishView view, String fallbackCode) {
        if (view == null) {
            return new IslandActionResult(false, "FAILED");
        }
        return new IslandActionResult(view.accepted(), view.code().isBlank() ? (view.accepted() ? fallbackCode : "FAILED") : view.code());
    }

    private static IslandActionResult action(MemberActionView view, String fallbackCode) {
        if (view == null) {
            return new IslandActionResult(false, "FAILED");
        }
        return new IslandActionResult(view.accepted(), view.accepted() ? fallbackCode : (view.code().isBlank() ? "FAILED" : view.code()));
    }

    private static IslandActionResult action(PermissionActionView view, String fallbackCode) {
        if (view == null) {
            return new IslandActionResult(false, "FAILED");
        }
        return new IslandActionResult(view.accepted(), view.accepted() ? fallbackCode : (view.code().isBlank() ? "FAILED" : view.code()));
    }

    private static IslandActionResult action(EnvironmentActionView view, String fallbackCode) {
        if (view == null) {
            return new IslandActionResult(false, "FAILED");
        }
        return new IslandActionResult(view.accepted(), view.accepted() ? fallbackCode : (view.code().isBlank() ? "FAILED" : view.code()));
    }

    private static IslandActionResult action(SettingsActionView view, String fallbackCode) {
        if (view == null) {
            return new IslandActionResult(false, "FAILED");
        }
        return new IslandActionResult(view.accepted(), view.accepted() ? fallbackCode : (view.code().isBlank() ? "FAILED" : view.code()));
    }

    private static IslandActionResult action(HomeWarpActionView view, String fallbackCode) {
        if (view == null) {
            return new IslandActionResult(false, "FAILED");
        }
        return new IslandActionResult(view.accepted(), view.accepted() ? fallbackCode : (view.code().isBlank() ? "FAILED" : view.code()));
    }

    private static IslandActionResult roleResetAction(MutationResult<CoreGuiViews.RoleView> result) {
        boolean changed = result != null
            && result.changed()
            && result.value() != null
            && result.value().role() != null
            && !result.value().role().isBlank();
        return new IslandActionResult(changed, changed ? "ROLE_RESET" : "FAILED");
    }

    private static IslandActionResult action(BlockValueActionView view, String fallbackCode) {
        if (view == null) {
            return new IslandActionResult(false, "FAILED");
        }
        return new IslandActionResult(view.accepted(), view.code().isBlank() ? (view.accepted() ? fallbackCode : "FAILED") : view.code());
    }

    private static List<IslandPermissionRuleSnapshot> permissionRules(UUID islandId, List<PermissionAssignmentView> views) {
        return views.stream()
            .filter(view -> view.playerUuid().isBlank())
            .map(view -> new IslandPermissionRuleSnapshot(
                islandId,
                normalizedRoleKey(view.role(), "VISITOR"),
                enumValue(IslandPermission.class, view.permission().isBlank() ? "INTERACT" : view.permission(), IslandPermission.INTERACT),
                view.allowed()
            ))
            .toList();
    }

    private static List<IslandRoleSnapshot> roles(UUID islandId, List<CoreGuiViews.RoleView> views) {
        return views.stream()
            .map(view -> new IslandRoleSnapshot(
                islandId,
                normalizedRoleKey(view.role(), "MEMBER"),
                view.weight(),
                view.displayName()
            ))
            .toList();
    }

    private static IslandRoleSnapshot role(UUID islandId, CoreGuiViews.RoleView view) {
        return new IslandRoleSnapshot(
            islandId == null ? new UUID(0L, 0L) : islandId,
            normalizedRoleKey(view == null ? "" : view.role(), "MEMBER"),
            view == null ? 0 : view.weight(),
            view == null ? "" : view.displayName()
        );
    }

    private static String normalizedRoleKey(String value, String fallback) {
        String roleKey = value == null || value.isBlank() ? fallback : value;
        return roleKey.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
    }

    private static IslandLimitSnapshot limit(UUID islandId, UUID actorUuid, EnvironmentActionView view) {
        return new IslandLimitSnapshot(
            view == null || view.islandId().isBlank() ? (islandId == null ? new UUID(0L, 0L) : islandId) : uuidValueOrZero(view.islandId()),
            view == null ? "" : view.key(),
            view == null ? 0L : view.value(),
            view == null || view.updatedBy().isBlank() ? (actorUuid == null ? new UUID(0L, 0L) : actorUuid) : uuidValueOrZero(view.updatedBy()),
            view == null || view.updatedAt().isBlank() ? Instant.EPOCH : instant(view.updatedAt())
        );
    }

    private static IslandBankChangeSnapshot bankChange(IslandBankChangeSnapshot view) {
        if (view == null) {
            return new IslandBankChangeSnapshot(false, "FAILED", null);
        }
        return bankChange(view, "");
    }

    private static IslandBankChangeSnapshot bankChange(IslandBankChangeSnapshot view, String acceptedCode) {
        String code = view.code().isBlank() && view.accepted() ? acceptedCode : view.code();
        if (code.isBlank() && !view.accepted()) {
            code = "FAILED";
        }
        return new IslandBankChangeSnapshot(view.accepted(), code, view.bank());
    }

    private static IslandChatResult chatResult(ChatActionView view) {
        if (view == null) {
            return new IslandChatResult(false, "", "");
        }
        return new IslandChatResult(view.accepted(), view.channel(), view.message());
    }

    private static IslandLevelSnapshot level(LevelView view) {
        if (view == null) {
            return new IslandLevelSnapshot(new UUID(0L, 0L), 0L, "0", Instant.EPOCH);
        }
        return new IslandLevelSnapshot(
            uuidValueOrZero(view.islandId()),
            view.level(),
            view.worth(),
            instant(view.calculatedAt())
        );
    }

    private static IslandLevelSnapshot level(CoreGuiViews.IslandInfoView view) {
        if (view == null) {
            return new IslandLevelSnapshot(new UUID(0L, 0L), 0L, "0", Instant.EPOCH);
        }
        return new IslandLevelSnapshot(
            uuidValueOrZero(view.islandId()),
            view.level(),
            view.worth().isBlank() ? "0" : view.worth(),
            view.updatedAt().isBlank() ? Instant.EPOCH : instant(view.updatedAt())
        );
    }

    private static List<IslandRankSnapshot> rankings(List<ProgressionRankingEntryView> views) {
        return views.stream()
            .map(view -> new IslandRankSnapshot(
                uuidValueOrZero(view.islandId()),
                view.level(),
                view.worth(),
                Instant.EPOCH
            ))
            .toList();
    }

    private static List<IslandReviewRankSnapshot> reviewRankings(List<ProgressionReviewRankingEntryView> views) {
        return views.stream()
            .map(view -> new IslandReviewRankSnapshot(
                uuidValueOrZero(view.islandId()),
                view.averageRating(),
                intValue(view.reviewCount()),
                Instant.EPOCH
            ))
            .toList();
    }

    private static List<IslandReviewSnapshot> reviews(UUID islandId, ReviewListView view) {
        UUID fallbackIslandId = islandId == null ? new UUID(0L, 0L) : islandId;
        return (view == null ? List.<ReviewView>of() : view.reviews()).stream()
            .map(review -> new IslandReviewSnapshot(
                review.islandId().isBlank() ? fallbackIslandId : uuidValueOrZero(review.islandId()),
                uuidValueOrZero(review.reviewerUuid()),
                intValue(review.rating()),
                review.comment(),
                instant(review.createdAt()),
                instant(review.updatedAt())
            ))
            .toList();
    }

    private static IslandVisitorStatsSnapshot visitorStats(IslandVisitorStatsView view) {
        List<IslandVisitorStatsSnapshot.RecentVisitor> recentVisitors = view.recentVisitors().stream()
            .map(visitor -> new IslandVisitorStatsSnapshot.RecentVisitor(
                visitor.visitorUuid(),
                visitor.lastVisitedAt().isBlank() ? Instant.EPOCH : instant(visitor.lastVisitedAt())
            ))
            .toList();
        return new IslandVisitorStatsSnapshot(
            uuidValueOrZero(view.islandId()),
            view.totalVisits(),
            view.uniqueVisitors(),
            recentVisitors
        );
    }

    private static List<IslandWarehouseItemSnapshot> warehouseItems(List<WarehouseItemView> views) {
        return views.stream()
            .map(view -> new IslandWarehouseItemSnapshot(
                uuidValueOrZero(view.islandId()),
                view.materialKey(),
                view.amount(),
                view.updatedAt().isBlank() ? Instant.EPOCH : instant(view.updatedAt())
            ))
            .toList();
    }

    private static List<IslandUpgradeSnapshot> upgrades(UUID islandId, List<CoreGuiViews.UpgradeView> views) {
        return views.stream()
            .map(view -> new IslandUpgradeSnapshot(
                islandId,
                view.key(),
                enumValue(UpgradeType.class, view.type().isBlank() ? "ISLAND_SIZE" : view.type(), UpgradeType.ISLAND_SIZE),
                view.level(),
                Instant.EPOCH
            ))
            .toList();
    }

    private static UpgradePurchaseSnapshot upgradePurchase(ProgressionUpgradePurchaseView view) {
        if (view == null) {
            return new UpgradePurchaseSnapshot(false, "", "0", null);
        }
        IslandUpgradeSnapshot upgrade = view.upgradeKey().isBlank()
            ? null
            : new IslandUpgradeSnapshot(
                uuidValueOrZero(view.islandId()),
                view.upgradeKey(),
                enumValue(UpgradeType.class, view.type().isBlank() ? "ISLAND_SIZE" : view.type(), UpgradeType.ISLAND_SIZE),
                intValue(view.level()),
                view.updatedAt().isBlank() ? Instant.EPOCH : instant(view.updatedAt())
            );
        return new UpgradePurchaseSnapshot(view.accepted(), view.code(), view.cost(), upgrade);
    }

    private static List<UpgradeRuleSnapshot> upgradeRules(List<UpgradeRuleView> views) {
        return views.stream()
            .map(view -> new UpgradeRuleSnapshot(
                view.key(),
                enumValue(UpgradeType.class, view.type().isBlank() ? "ISLAND_SIZE" : view.type(), UpgradeType.ISLAND_SIZE),
                intValue(view.maxLevel()),
                view.baseCost().isBlank() ? "0" : view.baseCost(),
                view.multiplier().isBlank() ? "1" : view.multiplier()
            ))
            .toList();
    }

    private static List<BlockValueSnapshot> blockValues(List<BlockValueView> views) {
        return (views == null ? List.<BlockValueView>of() : views).stream()
            .map(view -> new BlockValueSnapshot(
                view.materialKey(),
                view.worth(),
                view.levelPoints(),
                view.limit()
            ))
            .toList();
    }

    private static List<IslandMissionSnapshot> missions(UUID islandId, String kind, List<CoreGuiViews.MissionView> views) {
        String safeKind = kind == null || kind.isBlank() ? "MISSION" : kind;
        return views.stream()
            .map(view -> new IslandMissionSnapshot(
                islandId,
                view.key(),
                safeKind,
                view.title(),
                view.progress(),
                view.goal(),
                view.completed(),
                view.reward(),
                Instant.EPOCH
            ))
            .toList();
    }

    private static Optional<IslandMissionSnapshot> mission(ProgressionMissionCompletionView view) {
        if (view == null || view.missionKey().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new IslandMissionSnapshot(
            uuidValueOrZero(view.islandId()),
            view.missionKey(),
            view.kind().isBlank() ? "MISSION" : view.kind(),
            view.title(),
            view.progress(),
            view.goal(),
            view.completed(),
            view.reward(),
            view.updatedAt().isBlank() ? Instant.EPOCH : instant(view.updatedAt())
        ));
    }

    private static IslandLocation location(double x, double y, double z) {
        return new IslandLocation("", x, y, z, 0.0f, 0.0f);
    }

    private static NodeSweepResult nodeSweep(AdminNodeActionView view) {
        if (view == null) {
            return new NodeSweepResult(List.of(), 0);
        }
        return new NodeSweepResult(view.nodes(), view.recoveryRequired());
    }

    private static List<String> nodeIds(List<IslandNodeSnapshot> nodes) {
        return (nodes == null ? List.<IslandNodeSnapshot>of() : nodes).stream()
            .map(IslandNodeSnapshot::nodeId)
            .filter(nodeId -> nodeId != null && !nodeId.isBlank())
            .toList();
    }

    private static List<IslandTemplateSnapshot> templates(List<TemplateView> views) {
        return (views == null ? List.<TemplateView>of() : views).stream()
            .map(view -> new IslandTemplateSnapshot(
                view.id(),
                view.displayName(),
                view.enabled(),
                view.minNodeVersion()
            ))
            .toList();
    }

    private static IslandTemplateSnapshot template(TemplateView view) {
        if (view == null) {
            return new IslandTemplateSnapshot("", "", false, "");
        }
        return new IslandTemplateSnapshot(view.id(), view.displayName(), view.enabled(), view.minNodeVersion());
    }

    private static List<GlobalEventSnapshot> events(AdminEventStreamView stream) {
        return (stream == null ? List.<AdminEventView>of() : stream.events()).stream()
            .map(PaperCloudIslandsApi::event)
            .toList();
    }

    private static GlobalEventSnapshot event(AdminEventView view) {
        return new GlobalEventSnapshot(
            view == null ? 0L : view.seq(),
            view == null ? "" : view.type(),
            view == null ? Map.of() : view.fields(),
            instant(view == null ? "" : view.occurredAt())
        );
    }

    private static GlobalEventBatchSnapshot eventBatch(AdminEventStreamView stream) {
        return new GlobalEventBatchSnapshot(
            stream == null ? 0L : stream.oldestSeq(),
            stream == null ? 0L : stream.latestSeq(),
            events(stream)
        );
    }

    private static List<AuditLogSnapshot> auditLogs(List<AdminAuditEntryView> views) {
        return (views == null ? List.<AdminAuditEntryView>of() : views).stream()
            .map(view -> new AuditLogSnapshot(
                uuidValueOrZero(view.id()),
                uuidValue(view.actorUuid()),
                view.actorType(),
                view.action(),
                view.targetType(),
                view.targetId(),
                view.payload(),
                instant(view.createdAt())
            ))
            .toList();
    }

    private static RouteTicket routeTicket(AdminRouteTicketView view) {
        java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("targetServerName", view.targetServerName().isBlank() ? view.targetNode() : view.targetServerName());
        putPayloadValueIfPresent(payload, "targetType", view.targetType());
        putPayloadValueIfPresent(payload, "homeName", view.homeName());
        putPayloadValueIfPresent(payload, "warpName", view.warpName());
        return new RouteTicket(
            uuidValueOrZero(view.ticketId()),
            uuidValueOrZero(view.playerUuid()),
            enumValue(RouteAction.class, view.action().isBlank() ? "HOME" : view.action(), RouteAction.HOME),
            uuidValueOrZero(view.islandId()),
            view.targetNode(),
            view.targetWorld(),
            enumValue(RouteTicketState.class, view.state().isBlank() ? "READY" : view.state(), RouteTicketState.READY),
            instant(view.expiresAt().isBlank() ? Instant.EPOCH.toString() : view.expiresAt()),
            view.nonce(),
            Map.copyOf(payload)
        );
    }

    private static RouteClearResult routeClear(AdminRouteClearView view) {
        if (view == null) {
            return new RouteClearResult(false, false);
        }
        return new RouteClearResult(view.clearedSession(), view.clearedTicket());
    }

    private static CoreMaintenanceResult maintenance(String json, boolean reloaded) {
        return new CoreMaintenanceResult(
            reloaded,
            integer(json, "clearedSessions", 0),
            integer(json, "clearedTickets", 0)
        );
    }

    private static CoreMaintenanceResult maintenance(AdminMaintenanceResultView view) {
        if (view == null) {
            return new CoreMaintenanceResult(false, 0, 0);
        }
        return new CoreMaintenanceResult(
            view.reloaded(),
            Math.toIntExact(view.clearedSessions()),
            Math.toIntExact(view.clearedTickets())
        );
    }

    private static JobRecoveryResult jobRecovery(JobRecoveryView view) {
        if (view == null) {
            return new JobRecoveryResult(false, "", "FAILED");
        }
        return new JobRecoveryResult(view.accepted(), view.accepted() ? view.recovered() : "", view.code().isBlank() ? (view.accepted() ? "RECOVERED" : "FAILED") : view.code());
    }

    private static void putPayloadValueIfPresent(Map<String, String> payload, String field, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(field, value);
        }
    }

    private static List<IslandJobSnapshot> jobs(List<JobView> views) {
        return (views == null ? List.<JobView>of() : views).stream()
            .map(view -> new IslandJobSnapshot(
                uuidValueOrZero(view.id()),
                view.type(),
                uuidValueOrZero(view.islandId()),
                view.targetNode(),
                view.state(),
                view.priority(),
                intValue(view.attempts()),
                view.lockedBy(),
                view.error(),
                view.payload(),
                instant(view.createdAt().isBlank() ? Instant.EPOCH.toString() : view.createdAt()),
                instant(view.updatedAt().isBlank() ? Instant.EPOCH.toString() : view.updatedAt())
            ))
            .toList();
    }

    private static List<String> objects(String json, String arrayField) {
        return jsonArray(json, arrayField).stream()
            .map(SimpleJson::object)
            .filter(object -> !object.isEmpty())
            .map(SimpleJson::stringify)
            .toList();
    }

    private static Map<String, String> stringMap(String json, String field) {
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        SimpleJson.object(jsonObject(json).get(field)).forEach((key, value) -> values.put(SimpleJson.text(key), SimpleJson.text(value)));
        return Map.copyOf(values);
    }

    private static String objectValue(String json, String field) {
        Map<?, ?> object = SimpleJson.object(jsonObject(json).get(field));
        return object.isEmpty() ? "" : SimpleJson.stringify(object);
    }

    private static Map<String, Double> decimalMap(String json, String field) {
        java.util.LinkedHashMap<String, Double> values = new java.util.LinkedHashMap<>();
        SimpleJson.object(jsonObject(json).get(field)).forEach((key, value) -> {
            try {
                values.put(SimpleJson.text(key), Double.parseDouble(SimpleJson.text(value)));
            } catch (NumberFormatException ignored) {
                values.put(SimpleJson.text(key), 0.0D);
            }
        });
        return Map.copyOf(values);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String text(String json, String field, String fallback) {
        Map<?, ?> object = jsonObject(json);
        if (!object.containsKey(field)) {
            return fallback;
        }
        Object value = object.get(field);
        return value == null ? fallback : SimpleJson.text(value);
    }

    private static long number(String json, String field) {
        return SimpleJson.number(jsonObject(json).get(field));
    }

    private static String nullableText(String json, String field) {
        Map<?, ?> object = jsonObject(json);
        return object.containsKey(field) && object.get(field) == null ? null : text(json, field, null);
    }

    private static UUID uuid(String json, String field, UUID fallback) {
        try {
            return UUID.fromString(text(json, field, fallback.toString()));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static UUID nullableUuid(String json, String field) {
        String value = nullableText(json, field);
        if (value == null || value.isBlank()) {
            return null;
        }
        return uuidValue(value);
    }

    private static UUID uuidValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static UUID uuidValueOrZero(String value) {
        UUID uuid = uuidValue(value);
        return uuid == null ? new UUID(0L, 0L) : uuid;
    }

    private static String nullableBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Integer nullableInt(Long value) {
        return value == null ? null : intValue(value);
    }

    private static int intValue(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private static int integer(String json, String field, int fallback) {
        try {
            return Integer.parseInt(number(json, field, Integer.toString(fallback)));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static Integer nullableInteger(String json, String field) {
        Map<?, ?> object = jsonObject(json);
        return object.containsKey(field) && object.get(field) == null ? null : integer(json, field, 0);
    }

    private static long longValue(String json, String field, long fallback) {
        try {
            return Long.parseLong(number(json, field, Long.toString(fallback)));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double decimal(String json, String field, double fallback) {
        try {
            return Double.parseDouble(number(json, field, Double.toString(fallback)));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean bool(String json, String field, boolean fallback) {
        Map<?, ?> object = jsonObject(json);
        if (!object.containsKey(field)) {
            return fallback;
        }
        Object value = object.get(field);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(SimpleJson.text(value));
    }

    private static String number(String json, String field, String fallback) {
        Map<?, ?> object = jsonObject(json);
        if (!object.containsKey(field) || object.get(field) == null) {
            return fallback;
        }
        return SimpleJson.text(object.get(field));
    }

    private static String scalar(String json, String field) {
        Map<?, ?> object = jsonObject(json);
        if (!object.containsKey(field) || object.get(field) == null) {
            return null;
        }
        Object value = object.get(field);
        return value instanceof Number ? SimpleJson.text(value) : null;
    }

    private static Map<?, ?> jsonObject(String json) {
        return SimpleJson.object(SimpleJson.parse(json));
    }

    private static List<?> jsonArray(String json, String field) {
        return SimpleJson.list(jsonObject(json).get(field));
    }

    private static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }

    private static Instant nullableInstantValue(String value) {
        return value == null || value.isBlank() ? null : instant(value);
    }

    private static Instant nullableInstant(String json, String field) {
        String value = nullableText(json, field);
        return value == null ? null : instant(value);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

}
