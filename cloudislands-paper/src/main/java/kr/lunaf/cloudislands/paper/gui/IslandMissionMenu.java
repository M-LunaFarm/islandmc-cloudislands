package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.MissionView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class IslandMissionMenu implements Listener {
    private static final String MISSION_TITLE = "섬 미션";
    private static final String CHALLENGE_TITLE = "섬 챌린지";
    private static final String MISSION_MENU_ID = "island.missions";
    private static final String CHALLENGE_MENU_ID = "island.challenges";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/missions.yml",
        new GuiMenuDefinition(MISSION_MENU_ID, 6, "menu.missions.title", Map.of(
            "open", "island.missions.open",
            "complete", "island.mission.complete",
            "back", "island.main.open"
        ))
    );
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandMissionMenu() {
        this(null);
    }

    public IslandMissionMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandMissionMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, String kind) {
        open(plugin, client, player, islandId, kind, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, String kind, MessageRenderer messages) {
        boolean challenge = "CHALLENGE".equalsIgnoreCase(kind);
        GuiSession session = GuiSessions.begin(player, challenge ? CHALLENGE_MENU_ID : MISSION_MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), challenge ? CHALLENGE_TITLE : MISSION_TITLE));
        PaperGuiViews.islandMissions(client, islandId, kind)
            .thenAccept(missions -> openSync(plugin, player, session, kind, missions, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), challenge ? CHALLENGE_TITLE : MISSION_TITLE), message(messages, "mission-menu-load-failed", "섬 과제를 불러오지 못했습니다."), "island.missions.open", "island.main.open");
                return null;
            });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        boolean challenge = GuiInventories.isMenu(event.getView().getTopInventory(), CHALLENGE_MENU_ID);
        boolean mission = GuiInventories.isMenu(event.getView().getTopInventory(), MISSION_MENU_ID);
        if (!mission && !challenge) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null || !GuiItems.topInventoryClick(event)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) {
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.isBlank()) {
            return;
        }
        Map<String, String> data = new java.util.LinkedHashMap<>(GuiItems.data(event.getCurrentItem()));
        data.putIfAbsent("kind", mission ? "MISSION" : "CHALLENGE");
        player.closeInventory();
        if (actionId.equals("island.mission.complete")) {
            data.putIfAbsent("label", mission ? "섬 미션" : "섬 챌린지");
        }
        actions.execute(player, GuiActions.from(actionId, data).orElse(null), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, String kind, List<MissionView> missions, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            boolean challenge = "CHALLENGE".equalsIgnoreCase(kind);
            Inventory inventory = GuiInventories.create(challenge ? CHALLENGE_MENU_ID : MISSION_MENU_ID, session, MENU.size(), message(messages, MENU.titleKey(), challenge ? CHALLENGE_TITLE : MISSION_TITLE));
            GuiMenuRenderer.populate(inventory, MENU, messages, item -> !"E".equals(item.symbol()));
            MENU.itemAt(49).ifPresent(item -> inventory.setItem(49, GuiMenuRenderer.item(MENU, item, messages, Map.of("kind", challenge ? "CHALLENGE" : "MISSION"), List.of(challenge ? message(messages, "mission-menu-challenge-command", "/섬 챌린지") : message(messages, "mission-menu-mission-command", "/섬 미션")))));
            int slot = 0;
            for (MissionView mission : missions.stream().limit(45).toList()) {
                inventory.setItem(slot++, missionItem(mission, messages));
            }
            if (missions.isEmpty()) {
                setEmptyItem(inventory, messages);
            }
            player.openInventory(inventory);
        });
    }

    private static ItemStack missionItem(MissionView mission, MessageRenderer messages) {
        Material material = mission.completed() ? Material.LIME_DYE : Material.BOOK;
        String title = mission.title().isBlank() ? mission.key() : mission.title();
        return GuiItems.action(material, title, "island.mission.complete",
            Map.of("missionKey", mission.key()),
            message(messages, "mission-menu-progress", "진행도: ") + mission.progress() + "/" + mission.goal(),
            message(messages, "mission-menu-reward", "보상: ") + (mission.reward().isBlank() ? message(messages, "mission-menu-no-reward", "없음") : mission.reward()),
            mission.completed() ? message(messages, "mission-menu-completed", "완료됨") : message(messages, "mission-menu-click-to-complete", "클릭하면 완료를 요청합니다."));
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private static void setEmptyItem(Inventory inventory, MessageRenderer messages) {
        MENU.itemAt(22)
            .ifPresent(item -> inventory.setItem(22, GuiMenuRenderer.item(MENU, item, messages, Map.of(), List.of())));
    }

}
