package kr.lunaf.cloudislands.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.session.PlayerLocaleCache;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.WeatherType;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

public final class IslandGameplayFlagListener implements Listener {
    private final ProtectionController protection;
    private final MessageRenderer messages;
    private final PlayerLocaleCache locales;
    private final AdminFlightOverrides adminFlightOverrides;
    private final Map<UUID, EnvironmentOverride> environmentOverrides = new ConcurrentHashMap<>();

    public IslandGameplayFlagListener(ProtectionController protection) {
        this(protection, null);
    }

    public IslandGameplayFlagListener(ProtectionController protection, MessageRenderer messages) {
        this(protection, messages, null);
    }

    public IslandGameplayFlagListener(ProtectionController protection, MessageRenderer messages, PlayerLocaleCache locales) {
        this(protection, messages, locales, null);
    }

    public IslandGameplayFlagListener(ProtectionController protection, MessageRenderer messages, PlayerLocaleCache locales, AdminFlightOverrides adminFlightOverrides) {
        this.protection = protection;
        this.messages = messages;
        this.locales = locales;
        this.adminFlightOverrides = adminFlightOverrides;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Block block = event.getTo() == null ? player.getLocation().getBlock() : event.getTo().getBlock();
        updateEnvironment(player, block);
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        boolean allowed = adminFlightAllowed(player) || (protection.islandAt(block).isPresent() && islandFlagAllowed(block, IslandFlag.FLY));
        player.setAllowFlight(allowed);
        if (!allowed && player.isFlying()) {
            player.setFlying(false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!event.isFlying() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        boolean denied = protection.islandAt(player.getLocation().getBlock()).isPresent() && !adminFlightAllowed(player) && !islandFlagAllowed(player.getLocation().getBlock(), IslandFlag.FLY);
        event.setCancelled(denied);
        if (denied) {
            player.sendActionBar(Component.text(message(player, "flag-fly-denied", "이 섬에서는 비행할 수 없습니다.")));
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        updateFlight(event.getPlayer());
        updateEnvironment(event.getPlayer(), event.getPlayer().getLocation().getBlock());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearManagedFlight(event.getPlayer());
        clearEnvironment(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!islandFlagAllowed(event.getEntity().getLocation().getBlock(), IslandFlag.KEEP_INVENTORY)) {
            return;
        }
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (protection.islandAt(event.getLocation().getBlock()).isPresent() && !islandFlagAllowed(event.getLocation().getBlock(), IslandFlag.MOB_SPAWN)) {
            event.setCancelled(true);
            return;
        }
        IslandFlag flag = IslandSpawnFlagPolicy.categoryFlag(event.getEntityType());
        if (flag != null && protection.islandAt(event.getLocation().getBlock()).isPresent() && !islandFlagAllowed(event.getLocation().getBlock(), flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (attackingPlayer(event.getDamager()) == null || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Block block = victim.getLocation().getBlock();
        if (protection.islandAt(block).isPresent() && !islandFlagAllowed(block, IslandFlag.PVP)) {
            event.setCancelled(true);
            Player attacker = attackingPlayer(event.getDamager());
            if (attacker != null) {
                attacker.sendActionBar(Component.text(message(attacker, "flag-pvp-denied", "이 섬에서는 PVP가 비활성화되어 있습니다.")));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntityType() == EntityType.ENDERMAN && protection.islandAt(event.getBlock()).isPresent() && !islandFlagAllowed(event.getBlock(), IslandFlag.ENDERMAN_GRIEF)) {
            event.setCancelled(true);
        }
    }

    private Player attackingPlayer(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean islandFlagAllowed(Block block, IslandFlag flag) {
        return protection.islandAt(block).isPresent() && protection.checkSystemFlag(block, flag).allowed();
    }

    private String message(String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private String message(Player player, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plainForLocale(player == null ? "" : locales == null ? PlayerLocaleCache.clientLocale(player) : locales.locale(player), key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private void updateFlight(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Block block = player.getLocation().getBlock();
        boolean allowed = adminFlightAllowed(player) || (protection.islandAt(block).isPresent() && islandFlagAllowed(block, IslandFlag.FLY));
        player.setAllowFlight(allowed);
        if (!allowed && player.isFlying()) {
            player.setFlying(false);
        }
    }

    private void clearManagedFlight(Player player) {
        if (adminFlightOverrides != null) {
            adminFlightOverrides.clear(player.getUniqueId());
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    private boolean adminFlightAllowed(Player player) {
        return adminFlightOverrides != null && adminFlightOverrides.enabled(player);
    }

    private void updateEnvironment(Player player, Block block) {
        EnvironmentOverride desired = environmentOverride(block);
        EnvironmentOverride previous = environmentOverrides.put(player.getUniqueId(), desired);
        if (desired.equals(previous)) {
            return;
        }
        switch (desired.time()) {
            case DAY -> player.setPlayerTime(1000L, false);
            case MIDDLE_DAY -> player.setPlayerTime(6000L, false);
            case NIGHT -> player.setPlayerTime(13000L, false);
            case MIDDLE_NIGHT -> player.setPlayerTime(18000L, false);
            case DEFAULT -> player.resetPlayerTime();
        }
        switch (desired.weather()) {
            case RAIN -> player.setPlayerWeather(WeatherType.DOWNFALL);
            case SHINY -> player.setPlayerWeather(WeatherType.CLEAR);
            case DEFAULT -> player.resetPlayerWeather();
        }
    }

    private EnvironmentOverride environmentOverride(Block block) {
        if (protection.islandAt(block).isEmpty()) {
            return EnvironmentOverride.DEFAULT;
        }
        TimeOverride time = flag(block, IslandFlag.ALWAYS_MIDDLE_DAY) ? TimeOverride.MIDDLE_DAY
            : flag(block, IslandFlag.ALWAYS_DAY) ? TimeOverride.DAY
            : flag(block, IslandFlag.ALWAYS_MIDDLE_NIGHT) ? TimeOverride.MIDDLE_NIGHT
            : flag(block, IslandFlag.ALWAYS_NIGHT) ? TimeOverride.NIGHT
            : TimeOverride.DEFAULT;
        WeatherOverride weather = flag(block, IslandFlag.ALWAYS_RAIN) ? WeatherOverride.RAIN
            : flag(block, IslandFlag.ALWAYS_SHINY) ? WeatherOverride.SHINY
            : WeatherOverride.DEFAULT;
        return new EnvironmentOverride(time, weather);
    }

    private boolean flag(Block block, IslandFlag flag) {
        return protection.checkSystemFlag(block, flag).allowed();
    }

    private void clearEnvironment(Player player) {
        environmentOverrides.remove(player.getUniqueId());
        player.resetPlayerTime();
        player.resetPlayerWeather();
    }

    private enum TimeOverride {
        DEFAULT,
        DAY,
        MIDDLE_DAY,
        NIGHT,
        MIDDLE_NIGHT
    }

    private enum WeatherOverride {
        DEFAULT,
        RAIN,
        SHINY
    }

    private record EnvironmentOverride(TimeOverride time, WeatherOverride weather) {
        private static final EnvironmentOverride DEFAULT = new EnvironmentOverride(TimeOverride.DEFAULT, WeatherOverride.DEFAULT);
    }
}
