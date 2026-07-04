package kr.lunaf.cloudislands.paper.limit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.bootstrap.RuntimeComponent;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class IslandEffectApplier {
    private static final int EFFECT_DURATION_TICKS = 240;
    private static final Map<String, PotionEffectType> SUPPORTED_EFFECTS = supportedEffects();
    private final Plugin plugin;
    private final ProtectionController protection;
    private final IslandLimitCache limits;

    public IslandEffectApplier(Plugin plugin, ProtectionController protection, IslandLimitCache limits) {
        this.plugin = plugin;
        this.protection = protection;
        this.limits = limits;
    }

    public RuntimeComponent start() {
        BukkitTask task = PaperSchedulers.runTimer(plugin, this::applyOnlinePlayerEffects, 40L, 100L);
        return task::cancel;
    }

    void applyOnlinePlayerEffects() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            protection.islandAt(player.getLocation().getBlock()).ifPresent(islandId -> applyEffects(player, islandId));
        }
    }

    private void applyEffects(Player player, UUID islandId) {
        for (Map.Entry<String, PotionEffectType> entry : SUPPORTED_EFFECTS.entrySet()) {
            long amplifier = limits.limit(islandId, entry.getKey(), -1L);
            if (amplifier < 0L) {
                continue;
            }
            int safeAmplifier = (int) Math.min(10L, amplifier);
            player.addPotionEffect(new PotionEffect(entry.getValue(), EFFECT_DURATION_TICKS, safeAmplifier, true, false, true));
        }
    }

    @SuppressWarnings("deprecation")
    private static Map<String, PotionEffectType> supportedEffects() {
        Map<String, PotionEffectType> effects = new LinkedHashMap<>();
        putIfPresent(effects, "EFFECT:SPEED", PotionEffectType.getByName("SPEED"));
        putIfPresent(effects, "EFFECT:HASTE", PotionEffectType.getByName("FAST_DIGGING"));
        putIfPresent(effects, "EFFECT:JUMP_BOOST", PotionEffectType.getByName("JUMP"));
        putIfPresent(effects, "EFFECT:NIGHT_VISION", PotionEffectType.getByName("NIGHT_VISION"));
        putIfPresent(effects, "EFFECT:REGENERATION", PotionEffectType.getByName("REGENERATION"));
        return Map.copyOf(effects);
    }

    private static void putIfPresent(Map<String, PotionEffectType> effects, String key, PotionEffectType type) {
        if (type != null) {
            effects.put(key, type);
        }
    }
}
