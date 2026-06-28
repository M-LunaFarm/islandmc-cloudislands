package kr.lunaf.cloudislands.paper.mission;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.coreclient.ProgressionCommandClient;
import kr.lunaf.cloudislands.coreclient.ProgressionMissionCompletionView;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.event.IslandMissionCompleteEvent;
import kr.lunaf.cloudislands.paper.event.IslandMissionProgressEvent;
import kr.lunaf.cloudislands.paper.platform.event.PaperEvents;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

public final class IslandMissionProgressListener implements Listener {
    private final ProtectionController protection;
    private final ProgressionCommandClient progressionCommands;
    private final AtomicLong attempts = new AtomicLong();
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong ignored = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();

    public IslandMissionProgressListener(ProtectionController protection, ProgressionCommandClient progressionCommands) {
        if (protection == null) {
            throw new IllegalArgumentException("protection is required");
        }
        if (progressionCommands == null) {
            throw new IllegalArgumentException("progressionCommands is required");
        }
        this.protection = protection;
        this.progressionCommands = progressionCommands;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        progressAt(player, block, MissionProgressTriggers.blockBreak(materialKey(block.getType())));
        if (block.getBlockData() instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge()) {
            progressAt(player, block, MissionProgressTriggers.farmHarvest(materialKey(block.getType())));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        progressAt(event.getPlayer(), event.getBlockPlaced(), MissionProgressTriggers.blockPlace(materialKey(event.getBlockPlaced().getType())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            ignored.incrementAndGet();
            return;
        }
        Block block = event.getEntity().getLocation().getBlock();
        progressAt(killer, block, MissionProgressTriggers.mobKill(event.getEntityType().getKey().toString()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        progressAt(event.getPlayer(), event.getPlayer().getLocation().getBlock(), MissionProgressTriggers.fishingCatch());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getRecipe() == null) {
            ignored.incrementAndGet();
            return;
        }
        ItemStack result = event.getRecipe().getResult();
        progressAt(player, player.getLocation().getBlock(), MissionProgressTriggers.crafting(materialKey(result.getType()), result.getAmount()));
    }

    private void progressAt(Player player, Block block, java.util.List<MissionProgressTriggers.Trigger> triggers) {
        UUID islandId = protection.islandAt(block).orElse(null);
        if (islandId == null || !protection.memberOrTrusted(islandId, player.getUniqueId())) {
            ignored.incrementAndGet();
            return;
        }
        for (MissionProgressTriggers.Trigger trigger : triggers) {
            progress(islandId, player.getUniqueId(), trigger);
        }
    }

    private void progress(UUID islandId, UUID actorUuid, MissionProgressTriggers.Trigger trigger) {
        attempts.incrementAndGet();
        progressionCommands.progressMission(islandId, actorUuid, trigger.missionKey(), trigger.kind(), trigger.amount())
            .thenAccept(view -> handleProgress(islandId, trigger, view))
            .exceptionally(exception -> {
                failures.incrementAndGet();
                return null;
            });
    }

    private void handleProgress(UUID islandId, MissionProgressTriggers.Trigger trigger, ProgressionMissionCompletionView view) {
        if (!view.accepted()) {
            ignored.incrementAndGet();
            return;
        }
        accepted.incrementAndGet();
        Map<String, String> fields = Map.of(
            "missionKey", view.missionKey().isBlank() ? trigger.missionKey() : view.missionKey(),
            "kind", view.kind().isBlank() ? trigger.kind() : view.kind(),
            "progress", Long.toString(view.progress()),
            "goal", Long.toString(view.goal()),
            "amount", Long.toString(trigger.amount()),
            "completed", Boolean.toString(view.completed())
        );
        PaperEvents.call(new IslandMissionProgressEvent(
            islandId,
            fields.get("missionKey"),
            fields.get("kind"),
            view.progress(),
            view.goal(),
            trigger.amount(),
            view.completed(),
            fields
        ));
        if (view.completed()) {
            PaperEvents.call(new IslandMissionCompleteEvent(islandId, fields.get("missionKey"), fields.get("kind"), fields));
        }
    }

    private static String materialKey(Material material) {
        return material == null ? "" : material.getKey().toString();
    }

    public long attempts() {
        return attempts.get();
    }

    public long accepted() {
        return accepted.get();
    }

    public long ignored() {
        return ignored.get();
    }

    public long failures() {
        return failures.get();
    }

    public String eventPolicy() {
        return "BlockBreakEvent,BlockPlaceEvent,EntityDeathEvent,PlayerFishEvent,CraftItemEvent";
    }
}
