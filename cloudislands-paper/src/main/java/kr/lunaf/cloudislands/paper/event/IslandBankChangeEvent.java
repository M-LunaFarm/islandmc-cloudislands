package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandBankChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final UUID actorUuid;
    private final String operation;
    private final String amount;
    private final String balance;
    private final Map<String, String> fields;

    public IslandBankChangeEvent(UUID islandId, String operation, String amount, String balance, Map<String, String> fields) {
        this(islandId, null, operation, amount, balance, fields);
    }

    public IslandBankChangeEvent(UUID islandId, UUID actorUuid, String operation, String amount, String balance, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.actorUuid = actorUuid;
        this.operation = operation == null ? "" : operation;
        this.amount = amount == null ? "" : amount;
        this.balance = balance == null ? "" : balance;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public UUID actorUuid() {
        return actorUuid;
    }

    public String operation() {
        return operation;
    }

    public String amount() {
        return amount;
    }

    public String balance() {
        return balance;
    }

    public Map<String, String> fields() {
        return fields;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
