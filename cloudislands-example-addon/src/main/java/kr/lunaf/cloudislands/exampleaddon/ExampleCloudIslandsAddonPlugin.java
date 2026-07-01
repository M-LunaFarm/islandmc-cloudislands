package kr.lunaf.cloudislands.exampleaddon;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.CloudIslandsApiContract;
import kr.lunaf.cloudislands.api.CloudIslandsProvider;
import kr.lunaf.cloudislands.api.addon.CloudIslandsAddon;
import kr.lunaf.cloudislands.api.addon.CloudIslandsAddonBootstrap;
import kr.lunaf.cloudislands.api.event.CloudEvent;
import kr.lunaf.cloudislands.api.event.RouteTicketCreatedEvent;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;
import kr.lunaf.cloudislands.api.model.MissionProviderDefinitionSnapshot;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExampleCloudIslandsAddonPlugin extends JavaPlugin implements CloudIslandsAddon {
    private final ExampleCloudIslandsAddonDefinition definition = new ExampleCloudIslandsAddonDefinition(getPluginVersion());

    @Override
    public void onEnable() {
        CloudIslandsAddonBootstrap.registerIfAvailable(this).thenAccept(snapshot ->
            snapshot.ifPresent(addon -> getLogger().info("Registered " + addon.id() + " with CloudIslands API " + apiSummary()))
        );
    }

    private String apiSummary() {
        return CloudIslandsProvider.get()
            .map(this::apiSummary)
            .orElse("unavailable");
    }

    private String apiSummary(CloudIslandsApi api) {
        return api.apiRuntimeVersion()
            + " capabilities="
            + String.join(",", api.capabilities())
            + " warehouseQuery="
            + api.hasCapability(CloudIslandsApiContract.WAREHOUSE_QUERY_CAPABILITY);
    }

    @Override
    public void onDisable() {
        CloudIslandsAddonBootstrap.unregisterIfAvailable(this);
    }

    @Override
    public String addonId() {
        return definition.addonId();
    }

    @Override
    public String addonDisplayName() {
        return definition.addonDisplayName();
    }

    @Override
    public String addonVersion() {
        return definition.addonVersion();
    }

    @Override
    public Map<String, Boolean> addonFeatures() {
        return definition.addonFeatures();
    }

    @Override
    public Map<String, String> addonMetadata() {
        return definition.addonMetadata();
    }

    @Override
    public List<MissionProviderDefinitionSnapshot> addonMissions() {
        return definition.addonMissions();
    }

    @Override
    public void onAddonRegistered(CloudIslandsAddonSnapshot snapshot) {
        getLogger().info("CloudIslands addon state registered: " + snapshot.id());
    }

    @Override
    public void onCloudEvent(CloudEvent event) {
        if (event instanceof RouteTicketCreatedEvent routeTicket) {
            getLogger().fine("Observed CloudIslands route ticket " + routeTicket.ticketId());
        }
        CloudIslandsAddon.super.onCloudEvent(event);
    }

    private String getPluginVersion() {
        try {
            return getPluginMeta().getVersion();
        } catch (RuntimeException exception) {
            return "dev";
        }
    }
}
