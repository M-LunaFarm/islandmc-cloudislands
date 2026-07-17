package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.MigrationRunSnapshot;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

public final class AdminMigrationMenu implements Listener {
    private static final String TITLE_KEY = "admin-migration-menu-title";
    private static final String TITLE = "SuperiorSkyblock2 이전 마법사";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/admin-migration.yml",
        new GuiMenuDefinition("admin.migration", 3, TITLE_KEY, Map.ofEntries(
            Map.entry("status", "admin.migration.open"),
            Map.entry("scan", "admin.migration.scan"),
            Map.entry("dryrun", "admin.migration.dryrun"),
            Map.entry("approve", "admin.migration.approve.prompt"),
            Map.entry("import", "admin.migration.import.prompt"),
            Map.entry("verify", "admin.migration.verify"),
            Map.entry("rollback-plan", "admin.migration.rollback-plan"),
            Map.entry("rollback", "admin.migration.rollback.prompt"),
            Map.entry("close", "gui.close")
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final GuiActionRegistry actions;

    public AdminMigrationMenu() {
        this(null);
    }

    public AdminMigrationMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public AdminMigrationMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages,
            message(messages, "admin-migration-menu-loading", "마이그레이션 상태를 불러오는 중입니다."));
        client.migrations().migrateSuperiorSkyblock2("status", "")
            .thenAccept(status -> openSync(plugin, player, session, status, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages,
                    message(messages, TITLE_KEY, TITLE),
                    message(messages, "admin-migration-menu-load-failed", "마이그레이션 상태를 불러오지 못했습니다."),
                    "admin.migration.open",
                    "gui.close");
                return null;
            });
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, MigrationRunSnapshot status, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> true);
            GuiMenuRenderer.setSymbolItem(inventory, MENU, "H", messages, Map.of(), statusLore(status, messages));
            player.openInventory(inventory);
        });
    }

    public static Material rollbackConfirmationMaterial() {
        return GuiMenuRenderer.material(MENU, "R", "REDSTONE_BLOCK");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!GuiItems.menuClick(event, MENU_ID)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        player.closeInventory();
        if (actionId.equals("gui.close")) {
            return;
        }
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), GuiClick.from(event));
    }

    static List<String> statusLore(MigrationRunSnapshot status, MessageRenderer messages) {
        if (status == null) {
            return List.of(message(messages, "admin-migration-menu-state-prefix", "상태: ") + "UNKNOWN");
        }
        return List.of(
            message(messages, "admin-migration-menu-state-prefix", "상태: ") + safeLine(status.state(), 40),
            message(messages, "admin-migration-menu-path-prefix", "경로: ") + safeLine(status.path(), 48),
            message(messages, "admin-migration-menu-manifests-prefix", "매니페스트: ") + status.manifests(),
            message(messages, "admin-migration-menu-issues-prefix", "문제: ") + status.blockingIssues() + "/" + status.warningIssues(),
            message(messages, "admin-migration-menu-severity-prefix", "Dry-run 심각도: ") + safeLine(status.dryRunSeverity(), 24),
            message(messages, "admin-migration-menu-can-import-prefix", "가져오기 가능: ") + yesNo(status.canImport()),
            message(messages, "admin-migration-menu-token-ready-prefix", "승인 토큰 준비: ") + yesNo(status.approvalToken() != null && !status.approvalToken().isBlank()),
            message(messages, "admin-migration-menu-imported-prefix", "가져온 섬: ") + status.importedIslands() + "/" + status.expected(),
            message(messages, "admin-migration-menu-activation-prefix", "활성화 검증: ") + status.activationTestPassed() + "/" + status.activationTested(),
            message(messages, "admin-migration-menu-verify-prefix", "검증 통과: ") + yesNo(status.passed()),
            message(messages, "admin-migration-menu-rollback-prefix", "롤백 계획: ") + yesNo(status.rollbackPlanAvailable())
                + " / " + message(messages, "admin-migration-menu-rolled-back-label", "실행됨: ") + yesNo(status.rolledBack()),
            message(messages, "admin-migration-menu-refresh-action", "클릭: 상태 새로고침")
        );
    }

    private static String safeLine(String value, int maxLength) {
        String normalized = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.isBlank()) {
            return "-";
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 3) + "...";
    }

    private static String yesNo(boolean value) {
        return value ? "YES" : "NO";
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }
}
