package kr.lunaf.cloudislands.paper.mission;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.event.IslandMissionCompleteEvent;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class MissionRewardDeliveryListener implements Listener {
    private final Plugin plugin;

    public MissionRewardDeliveryListener(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin is required");
        }
        this.plugin = plugin;
    }

    @EventHandler
    public void onMissionComplete(IslandMissionCompleteEvent event) {
        Map<String, String> fields = event.fields();
        String rewardCode = fields.getOrDefault("rewardCode", "");
        if (rewardCode.equals("COMMAND_REWARD_QUEUED")) {
            scheduleCommandReward(fields);
        } else if (rewardCode.equals("ITEM_REWARD_QUEUED")) {
            scheduleItemReward(fields);
        }
    }

    private void scheduleCommandReward(Map<String, String> fields) {
        UUID actorUuid = actorUuid(fields);
        String template = fields.getOrDefault("command", "");
        if (template.isBlank()) {
            return;
        }
        PaperSchedulers.run(plugin, () -> {
            Player player = actorUuid == null ? null : plugin.getServer().getPlayer(actorUuid);
            String command = commandReward(template, actorUuid, player == null ? "" : player.getName());
            if (!command.isBlank()) {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
            }
        });
    }

    private void scheduleItemReward(Map<String, String> fields) {
        UUID actorUuid = actorUuid(fields);
        if (actorUuid == null) {
            return;
        }
        ItemReward reward = itemReward(fields.getOrDefault("item", ""));
        if (reward == null) {
            return;
        }
        PaperSchedulers.run(plugin, () -> {
            Player player = plugin.getServer().getPlayer(actorUuid);
            if (player == null || !player.isOnline()) {
                return;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(reward.material(), reward.amount()));
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            player.sendMessage(Component.text("Mission reward received: " + reward.material().getKey() + " x" + reward.amount()));
        });
    }

    static String commandReward(String template, UUID actorUuid, String playerName) {
        if (template == null || template.isBlank()) {
            return "";
        }
        String safeUuid = actorUuid == null ? "" : actorUuid.toString();
        String safePlayerName = playerName == null ? "" : playerName;
        return template.trim()
            .replace("%player%", safePlayerName)
            .replace("{player}", safePlayerName)
            .replace("%uuid%", safeUuid)
            .replace("{uuid}", safeUuid)
            .replaceFirst("^/", "");
    }

    static ItemReward itemReward(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] tokens = value.trim().split("\\s+");
        Material material = Material.matchMaterial(tokens[0].toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""));
        if (material == null || !material.isItem()) {
            return null;
        }
        int amount = 1;
        if (tokens.length > 1) {
            try {
                amount = Integer.parseInt(tokens[1]);
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }
        amount = Math.max(1, Math.min(amount, material.getMaxStackSize() * 36));
        return new ItemReward(material, amount);
    }

    private static UUID actorUuid(Map<String, String> fields) {
        String value = fields.getOrDefault("actorUuid", "");
        if (value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    record ItemReward(Material material, int amount) {
    }
}
