package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.MissionView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class IslandMissionMenu implements Listener {
    private static final String MISSION_TITLE = "섬 미션";
    private static final String CHALLENGE_TITLE = "섬 챌린지";
    private static final String MISSION_MENU_ID = "island.missions";
    private static final String CHALLENGE_MENU_ID = "island.challenges";
    private final MessageRenderer messages;

    public IslandMissionMenu() {
        this(null);
    }

    public IslandMissionMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, String kind) {
        open(plugin, client, player, islandId, kind, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, String kind, MessageRenderer messages) {
        boolean challenge = "CHALLENGE".equalsIgnoreCase(kind);
        GuiSession session = GuiSessions.begin(player, challenge ? CHALLENGE_MENU_ID : MISSION_MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, challenge ? CHALLENGE_TITLE : MISSION_TITLE);
        PaperGuiViews.islandMissions(client, islandId, kind)
            .thenAccept(missions -> openSync(plugin, player, session, kind, missions, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, challenge ? CHALLENGE_TITLE : MISSION_TITLE, message(messages, "mission-menu-load-failed", "섬 과제를 불러오지 못했습니다."), "island.missions.open", "island.main.open");
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
        player.closeInventory();
        if (slot == 45) {
            GuiActionRegistry.execute(player, "island.missions.open", java.util.Map.of("kind", "MISSION"), GuiClick.from(event));
            return;
        }
        if (slot == 53) {
            GuiActionRegistry.execute(player, "island.missions.open", java.util.Map.of("kind", "CHALLENGE"), GuiClick.from(event));
            return;
        }
        if (slot == 49) {
            GuiActionRegistry.execute(player, "island.missions.open", java.util.Map.of("kind", mission ? "MISSION" : "CHALLENGE"), GuiClick.from(event));
            return;
        }
        String missionKey = GuiItems.data(event.getCurrentItem()).getOrDefault("missionKey", "");
        if (missionKey.isBlank()) {
            return;
        }
        GuiActionRegistry.execute(player, "island.mission.complete", java.util.Map.of("kind", mission ? "MISSION" : "CHALLENGE", "missionKey", missionKey, "label", mission ? "섬 미션" : "섬 챌린지"), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, String kind, List<MissionView> missions, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            boolean challenge = "CHALLENGE".equalsIgnoreCase(kind);
            Inventory inventory = GuiInventories.create(challenge ? CHALLENGE_MENU_ID : MISSION_MENU_ID, 54, challenge ? CHALLENGE_TITLE : MISSION_TITLE);
            int slot = 0;
            for (MissionView mission : missions.stream().limit(45).toList()) {
                inventory.setItem(slot++, missionItem(mission, messages));
            }
            if (missions.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, message(messages, "mission-menu-empty-title", "과제 없음"), message(messages, "mission-menu-empty", "현재 표시할 섬 과제가 없습니다.")));
            }
            inventory.setItem(45, item(Material.BOOK, message(messages, "mission-menu-mission-name", "미션 보기"), message(messages, "mission-menu-mission-command", "/섬 미션")));
            inventory.setItem(49, item(Material.CLOCK, message(messages, "mission-menu-refresh-name", "새로고침"), challenge ? message(messages, "mission-menu-challenge-command", "/섬 챌린지") : message(messages, "mission-menu-mission-command", "/섬 미션")));
            inventory.setItem(53, item(Material.WRITABLE_BOOK, message(messages, "mission-menu-challenge-name", "챌린지 보기"), message(messages, "mission-menu-challenge-command", "/섬 챌린지")));
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
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

}
