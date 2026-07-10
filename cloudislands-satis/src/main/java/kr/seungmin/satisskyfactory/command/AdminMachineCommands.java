package kr.seungmin.satisskyfactory.command;

import java.util.Map;
import java.util.function.Predicate;
import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.util.NumberFormatter;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class AdminMachineCommands {
    private final FactoryIslandService islands;
    private final MachineService machines;
    private final PowerNetworkService power;
    private final MessageService messages;
    private final Predicate<String> featureEnabled;

    AdminMachineCommands(FactoryIslandService islands, MachineService machines, PowerNetworkService power,
                         MessageService messages, Predicate<String> featureEnabled) {
        this.islands = islands;
        this.machines = machines;
        this.power = power;
        this.messages = messages;
        this.featureEnabled = featureEnabled;
    }

    void debug(CommandSender sender, String[] args) {
        if (args.length < 3 || !(sender instanceof Player player)) {
            return;
        }
        if (args[2].equalsIgnoreCase("networks") && !requireFeature(sender, "machines")) {
            return;
        }
        islands.existingContext(player).ifPresent(context -> {
            if (args[2].equalsIgnoreCase("island")) {
                messages.send(sender, "debug-island", Map.of("island", context.factoryIsland().islandUuid().toString()));
            } else if (args[2].equalsIgnoreCase("networks")) {
                var state = power.state(context.factoryIsland().islandUuid());
                messages.send(sender, "debug-networks", Map.of(
                    "machines", String.valueOf(machines.byIsland(context.factoryIsland().islandUuid()).size()),
                    "ratio", NumberFormatter.ratio(state.ratio()),
                    "generation", NumberFormatter.decimal(state.generation(), 1),
                    "consumption", NumberFormatter.decimal(state.consumption(), 1),
                    "battery", NumberFormatter.decimal(state.batteryStored(), 1) + "/" + NumberFormatter.whole(state.batteryCapacity())
                ));
            }
        });
    }

    void removeHere(CommandSender sender) {
        if (!requireFeature(sender, "machines")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "no-player");
            return;
        }
        Block block = player.getTargetBlockExact(8);
        if (block == null || block.getType() == Material.AIR) {
            messages.send(sender, "no-target-block");
            return;
        }
        machines.at(block.getLocation()).ifPresentOrElse(machine -> {
            if (!machines.forceRemove(machine)) {
                messages.send(sender, "machine-reclaim-storage-full");
                return;
            }
            block.setType(Material.AIR, false);
            messages.send(sender, "machine-removed-admin");
        }, () -> messages.send(sender, "no-machine-here"));
    }

    void repairHere(CommandSender sender) {
        if (!requireFeature(sender, "machines")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "no-player");
            return;
        }
        Block block = player.getTargetBlockExact(8);
        if (block == null || block.getType() == Material.AIR) {
            messages.send(sender, "no-target-block");
            return;
        }
        machines.at(block.getLocation()).ifPresentOrElse(machine -> {
            if (!repair(machine)) {
                messages.send(sender, "feature-disabled", Map.of("feature", "machines"));
                return;
            }
            messages.send(sender, "machine-repaired");
        }, () -> messages.send(sender, "no-machine-here"));
    }

    private boolean repair(MachineInstance machine) {
        double previousWear = machine.wear();
        MachineStatus previousStatus = machine.status();
        machine.wear(0.0);
        machine.status(MachineStatus.SLEEPING);
        if (!machines.save(machine)) {
            machine.wear(previousWear);
            machine.status(previousStatus);
            return false;
        }
        return true;
    }

    private boolean requireFeature(CommandSender sender, String feature) {
        if (enabled(feature)) {
            return true;
        }
        messages.send(sender, "feature-disabled", Map.of("feature", feature));
        return false;
    }

    private boolean enabled(String feature) {
        if (featureEnabled == null) {
            return true;
        }
        try {
            return featureEnabled.test(feature);
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
