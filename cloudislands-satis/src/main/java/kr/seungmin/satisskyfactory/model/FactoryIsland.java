package kr.seungmin.satisskyfactory.model;

import java.util.UUID;

public final class FactoryIsland {
    private final UUID islandUuid;
    private UUID ownerUuid;
    private int tier;
    private long researchPoints;
    private long reputation;
    private long maintenanceDebt;
    private MaintenanceStatus maintenanceStatus;
    private long factoryScore;
    private long lastMaintenanceAt;
    private long lastTickAt;
    private long createdAt;
    private long updatedAt;
    private int emergencyContractsUsedToday;
    private String activeWorld = "";
    private int activeCenterX;
    private int activeCenterY;
    private int activeCenterZ;
    private String pendingMachineRemapWorld = "";
    private int pendingMachineRemapCenterX;
    private int pendingMachineRemapCenterY;
    private int pendingMachineRemapCenterZ;
    private String pendingResourceNodeRemapWorld = "";
    private int pendingResourceNodeRemapCenterX;
    private int pendingResourceNodeRemapCenterY;
    private int pendingResourceNodeRemapCenterZ;

    public FactoryIsland(UUID islandUuid, UUID ownerUuid) {
        this.islandUuid = islandUuid;
        this.ownerUuid = ownerUuid;
        this.tier = 1;
        this.maintenanceStatus = MaintenanceStatus.NORMAL;
        this.createdAt = System.currentTimeMillis();
    }

    public UUID islandUuid() {
        return islandUuid;
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public void ownerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public int tier() {
        return tier;
    }

    public void tier(int tier) {
        this.tier = tier;
    }

    public long researchPoints() {
        return researchPoints;
    }

    public void researchPoints(long researchPoints) {
        this.researchPoints = Math.max(0L, researchPoints);
    }

    public long addResearchPoints(long amount) {
        researchPoints = saturatingNonNegativeAdd(researchPoints, amount);
        return researchPoints;
    }

    public long reputation() {
        return reputation;
    }

    public void reputation(long reputation) {
        this.reputation = Math.max(0L, reputation);
    }

    public long addReputation(long amount) {
        reputation = saturatingNonNegativeAdd(reputation, amount);
        return reputation;
    }

    public long maintenanceDebt() {
        return maintenanceDebt;
    }

    public void maintenanceDebt(long maintenanceDebt) {
        this.maintenanceDebt = Math.max(0L, maintenanceDebt);
    }

    public long addMaintenanceDebt(long amount) {
        maintenanceDebt = saturatingNonNegativeAdd(maintenanceDebt, amount);
        return maintenanceDebt;
    }

    private static long saturatingNonNegativeAdd(long current, long amount) {
        try {
            return Math.max(0L, Math.addExact(current, amount));
        } catch (ArithmeticException overflow) {
            return amount > 0L ? Long.MAX_VALUE : 0L;
        }
    }

    public MaintenanceStatus maintenanceStatus() {
        return maintenanceStatus;
    }

    public void maintenanceStatus(MaintenanceStatus maintenanceStatus) {
        this.maintenanceStatus = maintenanceStatus;
    }

    public long factoryScore() {
        return factoryScore;
    }

    public void factoryScore(long factoryScore) {
        this.factoryScore = factoryScore;
    }

    public long lastMaintenanceAt() {
        return lastMaintenanceAt;
    }

    public void lastMaintenanceAt(long lastMaintenanceAt) {
        this.lastMaintenanceAt = lastMaintenanceAt;
    }

    public long lastTickAt() {
        return lastTickAt;
    }

    public void lastTickAt(long lastTickAt) {
        this.lastTickAt = lastTickAt;
    }

    public long createdAt() {
        return createdAt;
    }

    public void createdAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long updatedAt() {
        return updatedAt;
    }

    public void updatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int emergencyContractsUsedToday() {
        return emergencyContractsUsedToday;
    }

    public void emergencyContractsUsedToday(int emergencyContractsUsedToday) {
        this.emergencyContractsUsedToday = emergencyContractsUsedToday;
    }

    public String activeWorld() {
        return activeWorld;
    }

    public void activeWorld(String activeWorld) {
        this.activeWorld = activeWorld == null ? "" : activeWorld;
    }

    public int activeCenterX() {
        return activeCenterX;
    }

    public void activeCenterX(int activeCenterX) {
        this.activeCenterX = activeCenterX;
    }

    public int activeCenterY() {
        return activeCenterY;
    }

    public void activeCenterY(int activeCenterY) {
        this.activeCenterY = activeCenterY;
    }

    public int activeCenterZ() {
        return activeCenterZ;
    }

    public void activeCenterZ(int activeCenterZ) {
        this.activeCenterZ = activeCenterZ;
    }

    public boolean hasActiveCenter() {
        return activeWorld != null && !activeWorld.isBlank();
    }

    public boolean hasPendingMachineRemap() {
        return pendingMachineRemapWorld != null && !pendingMachineRemapWorld.isBlank();
    }

    public String pendingMachineRemapWorld() {
        return pendingMachineRemapWorld;
    }

    public int pendingMachineRemapCenterX() {
        return pendingMachineRemapCenterX;
    }

    public int pendingMachineRemapCenterY() {
        return pendingMachineRemapCenterY;
    }

    public int pendingMachineRemapCenterZ() {
        return pendingMachineRemapCenterZ;
    }

    public void pendingMachineRemap(String world, int centerX, int centerY, int centerZ) {
        this.pendingMachineRemapWorld = world == null ? "" : world;
        this.pendingMachineRemapCenterX = centerX;
        this.pendingMachineRemapCenterY = centerY;
        this.pendingMachineRemapCenterZ = centerZ;
    }

    public void clearPendingMachineRemap() {
        pendingMachineRemap("", 0, 0, 0);
    }

    public boolean hasPendingResourceNodeRemap() {
        return pendingResourceNodeRemapWorld != null && !pendingResourceNodeRemapWorld.isBlank();
    }

    public String pendingResourceNodeRemapWorld() {
        return pendingResourceNodeRemapWorld;
    }

    public int pendingResourceNodeRemapCenterX() {
        return pendingResourceNodeRemapCenterX;
    }

    public int pendingResourceNodeRemapCenterY() {
        return pendingResourceNodeRemapCenterY;
    }

    public int pendingResourceNodeRemapCenterZ() {
        return pendingResourceNodeRemapCenterZ;
    }

    public void pendingResourceNodeRemap(String world, int centerX, int centerY, int centerZ) {
        this.pendingResourceNodeRemapWorld = world == null ? "" : world;
        this.pendingResourceNodeRemapCenterX = centerX;
        this.pendingResourceNodeRemapCenterY = centerY;
        this.pendingResourceNodeRemapCenterZ = centerZ;
    }

    public void clearPendingResourceNodeRemap() {
        pendingResourceNodeRemap("", 0, 0, 0);
    }
}
