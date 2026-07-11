package kr.seungmin.satisskyfactory.gui;

import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.contract.ContractTemplate;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.item.ItemDefinition;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.IslandBoostService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.market.MarketService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.recipe.RecipeDefinition;
import kr.seungmin.satisskyfactory.recipe.RecipeService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import kr.seungmin.satisskyfactory.research.UnlockDefinition;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import kr.seungmin.satisskyfactory.util.NumberFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class FactoryGuiService {
    private final StorageService storage;
    private final ItemRegistry items;
    private final MachineDefinitionService definitions;
    private final RecipeService recipes;
    private final FactoryIslandService islands;
    private final ResearchService research;
    private final EconomyService economy;
    private final MessageService messages;
    private final Predicate<String> featureEnabled;

    public FactoryGuiService(StorageService storage, ItemRegistry items, MachineDefinitionService definitions,
                             RecipeService recipes, FactoryIslandService islands, ResearchService research,
                             EconomyService economy, MessageService messages, Predicate<String> featureEnabled) {
        this.storage = storage;
        this.items = items;
        this.definitions = definitions;
        this.recipes = recipes;
        this.islands = islands;
        this.research = research;
        this.economy = economy;
        this.messages = messages;
        this.featureEnabled = featureEnabled;
    }

    public void openMain(Player player, FactoryIsland island, int machineCount, PowerNetworkService.NetworkState powerState,
                         IslandBoostService.Boosts boosts) {
        if (!requireGui(player)) {
            return;
        }
        FactoryGuiHolder holder = new FactoryGuiHolder("main", island.islandUuid(), null);
        Inventory inventory = Bukkit.createInventory(holder, 27, title("main-title", "SatisSkyFactory"));
        holder.inventory(inventory);
        if (enabled("machines") && powerState != null) {
            List<Component> factoryLore = new ArrayList<>();
            factoryLore.add(gray("gui-tier", "티어: {tier}", Map.of("tier", String.valueOf(island.tier()))));
            factoryLore.add(gray("gui-machines", "기계 수: {count}", Map.of("count", String.valueOf(machineCount))));
            if (enabled("storage")) {
                factoryLore.add(gray("gui-storage-used", "저장고 사용량: {used}", Map.of("used", String.valueOf(storage.findIslandStorage(island.islandUuid())
                        .map(VirtualInventory::used)
                        .orElse(0L)))));
            }
            inventory.setItem(10, icon(Material.CRAFTING_TABLE, line(NamedTextColor.GOLD, "gui-factory", "공장"),
                    factoryLore));
            inventory.setItem(12, icon(Material.REDSTONE, line(NamedTextColor.RED, "gui-power", "전력"),
                    List.of(gray("gui-power-ratio", "전력 비율: {ratio}", Map.of("ratio", NumberFormatter.ratio(powerState.ratio()))),
                            gray("gui-generation", "생산량: {value}", Map.of("value", NumberFormatter.decimal(powerState.generation(), 1))),
                            gray("gui-consumption", "소비량: {value}", Map.of("value", NumberFormatter.decimal(powerState.consumption(), 1))),
                            gray("gui-battery", "배터리: {stored}/{capacity}", Map.of(
                                    "stored", NumberFormatter.decimal(powerState.batteryStored(), 1),
                                    "capacity", NumberFormatter.whole(powerState.batteryCapacity()))))));
        } else {
            inventory.setItem(10, icon(Material.CRAFTING_TABLE, line(NamedTextColor.DARK_GRAY, "gui-factory", "공장"),
                    List.of(gray("gui-machine-features-disabled", "기계 기능이 비활성화되어 있습니다."))));
        }
        inventory.setItem(14, icon(Material.EMERALD, line(NamedTextColor.GREEN, "gui-economy", "경제"),
                economyLore(island)));
        if (enabled("research")) {
            holder.action(16, "main_research", "");
            inventory.setItem(16, icon(Material.EXPERIENCE_BOTTLE, line(NamedTextColor.AQUA, "gui-research", "연구"),
                    researchMainLore(island, boosts)));
        }
        if (player.hasPermission("satisskyfactory.admin")) {
            holder.action(8, "main_admin", "");
            inventory.setItem(8, icon(Material.COMMAND_BLOCK, line(NamedTextColor.RED, "gui-admin", "관리"),
                    List.of(gray("gui-open-admin", "공장 관리 메뉴를 엽니다."))));
        }
        if (enabled("contracts") && enabled("storage")) {
            holder.action(20, "main_contracts", "");
            inventory.setItem(20, icon(Material.WRITABLE_BOOK, line(NamedTextColor.GOLD, "gui-contracts", "계약"),
                    List.of(gray("gui-open-contracts", "납품 계약을 엽니다."))));
        }
        if (enabled("market") && enabled("storage")) {
            holder.action(22, "main_market", "");
            inventory.setItem(22, icon(Material.EMERALD, line(NamedTextColor.GREEN, "gui-market", "시장"),
                    List.of(gray("gui-open-market", "저장된 공장 아이템을 판매합니다."))));
        }
        if (enabled("storage")) {
            holder.action(24, "main_storage", "");
            inventory.setItem(24, icon(Material.CHEST, line(NamedTextColor.YELLOW, "gui-storage", "저장고"),
                    List.of(gray("gui-open-storage", "섬 가상 저장고를 봅니다."))));
        }
        player.openInventory(inventory);
    }

    private boolean requireGui(Player player) {
        if (enabled("gui")) {
            return true;
        }
        messages.send(player, "feature-disabled", Map.of("feature", "gui"));
        player.closeInventory();
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

    private List<Component> economyLore(FactoryIsland island) {
        List<Component> lore = new ArrayList<>();
        if (enabled("maintenance")) {
            lore.add(gray("gui-debt", "부채: {debt}", Map.of("debt", String.valueOf(island.maintenanceDebt()))));
            lore.add(gray("gui-status", "상태: {status}", Map.of("status", island.maintenanceStatus().name())));
        }
        lore.add(gray("gui-reputation", "평판: {reputation}", Map.of("reputation", String.valueOf(island.reputation()))));
        return lore;
    }

    private List<Component> marketLore(FactoryIsland island, int safePage, int maxPage) {
        List<Component> lore = new ArrayList<>();
        if (enabled("maintenance")) {
            lore.add(gray("gui-debt", "부채: {debt}", Map.of("debt", String.valueOf(island.maintenanceDebt()))));
        }
        lore.add(gray("gui-page", "페이지: {page}/{pages}", Map.of(
                "page", String.valueOf(safePage + 1),
                "pages", String.valueOf(maxPage + 1))));
        return lore;
    }

    private List<Component> adminLore(FactoryIsland island, int machineCount, PowerNetworkService.NetworkState powerState) {
        List<Component> lore = new ArrayList<>();
        lore.add(gray("gui-island", "섬: {island}", Map.of("island", island.islandUuid().toString())));
        if (enabled("machines") && powerState != null) {
            lore.add(gray("gui-machines", "기계 수: {count}", Map.of("count", String.valueOf(machineCount))));
            lore.add(gray("gui-power-ratio", "전력 비율: {ratio}", Map.of("ratio", NumberFormatter.ratio(powerState.ratio()))));
        } else {
            lore.add(gray("gui-machine-features-disabled", "기계 기능이 비활성화되어 있습니다."));
        }
        return lore;
    }

    private List<Component> researchMainLore(FactoryIsland island, IslandBoostService.Boosts boosts) {
        List<Component> lore = new ArrayList<>();
        lore.add(gray("gui-points", "포인트: {points}", Map.of("points", String.valueOf(island.researchPoints()))));
        if (enabled("machines")) {
            lore.add(gray("gui-agriculture-boost", "농업 x{ratio}", Map.of("ratio", NumberFormatter.ratio(boosts.agricultureBoost()))));
            lore.add(gray("gui-machine-slots", "기계 슬롯 +{slots}", Map.of("slots", String.valueOf(boosts.factorySlotBonus()))));
        }
        if (enabled("contracts") && enabled("storage")) {
            lore.add(gray("gui-contract-slots", "계약 슬롯 +{slots}", Map.of("slots", String.valueOf(boosts.contractSlotBonus()))));
        }
        return lore;
    }

    public void openAdmin(Player player, FactoryIsland island, int machineCount, PowerNetworkService.NetworkState powerState) {
        if (!requireGui(player)) {
            return;
        }
        FactoryGuiHolder holder = new FactoryGuiHolder("admin", island.islandUuid(), null);
        Inventory inventory = Bukkit.createInventory(holder, 27, title("admin-title", "공장 관리"));
        holder.inventory(inventory);
        inventory.setItem(4, icon(Material.COMMAND_BLOCK, line(NamedTextColor.RED, "gui-admin", "관리"),
                adminLore(island, machineCount, powerState)));
        holder.action(10, "admin_reload", "");
        inventory.setItem(10, icon(Material.REDSTONE_TORCH, line(NamedTextColor.YELLOW, "gui-reload", "다시 불러오기"),
                List.of(gray("gui-reload-lore", "설정과 네트워크를 다시 구성합니다."))));
        holder.action(12, "admin_debug_island", "");
        inventory.setItem(12, icon(Material.MAP, line(NamedTextColor.AQUA, "gui-island-debug", "섬 디버그"),
                List.of(gray("gui-island-debug-lore", "섬 ID를 채팅으로 출력합니다."))));
        if (enabled("machines")) {
            holder.action(14, "admin_debug_networks", "");
            inventory.setItem(14, icon(Material.REDSTONE, line(NamedTextColor.RED, "gui-network-debug", "네트워크 디버그"),
                    List.of(gray("gui-network-debug-lore", "전력과 기계 상태를 채팅으로 출력합니다."))));
        }
        holder.action(22, "admin_back", "");
        inventory.setItem(22, icon(Material.ARROW, line(NamedTextColor.YELLOW, "gui-back", "뒤로"),
                List.of(gray("gui-back-main", "메인 공장 메뉴로 돌아갑니다."))));
        player.openInventory(inventory);
    }

    public void openStorage(Player player, FactoryIsland island) {
        openStorage(player, island, 0);
    }

    public void openStorage(Player player, FactoryIsland island, int page) {
        if (!requireGui(player)) {
            return;
        }
        if (!enabled("storage")) {
            messages.send(player, "feature-disabled", Map.of("feature", "storage"));
            player.closeInventory();
            return;
        }
        int safePage = Math.max(0, page);
        FactoryGuiHolder holder = new FactoryGuiHolder("storage", island.islandUuid(), null, safePage);
        Inventory inventory = Bukkit.createInventory(holder, 54, title("storage-title", "공장 저장고"));
        holder.inventory(inventory);
        VirtualInventory virtual = storage.islandStorageIfAllowed(island.islandUuid()).orElse(null);
        if (virtual == null) {
            messages.send(player, "feature-disabled", Map.of("feature", "storage"));
            player.closeInventory();
            return;
        }
        List<Map.Entry<String, Long>> entries = virtual.items().entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .toList();
        int pageSize = 45;
        int maxPage = Math.max(0, (entries.size() - 1) / pageSize);
        if (safePage > maxPage) {
            openStorage(player, island, maxPage);
            return;
        }
        holder.action(45, "storage_page", String.valueOf(Math.max(0, safePage - 1)));
        inventory.setItem(45, icon(Material.ARROW, line(NamedTextColor.YELLOW, "gui-previous-page", "이전 페이지"),
                List.of(gray("gui-page-of", "페이지 {page}/{pages}", Map.of(
                        "page", String.valueOf(safePage + 1),
                        "pages", String.valueOf(maxPage + 1))))));
        inventory.setItem(49, icon(Material.BOOK, line(NamedTextColor.AQUA, "gui-storage", "저장고"),
                List.of(gray("gui-used-capacity", "사용량: {used}/{capacity}", Map.of(
                                "used", String.valueOf(virtual.used()),
                                "capacity", String.valueOf(virtual.capacity()))),
                        gray("gui-page", "페이지: {page}/{pages}", Map.of(
                                "page", String.valueOf(safePage + 1),
                                "pages", String.valueOf(maxPage + 1))))));
        holder.action(53, "deposit_hand", "");
        inventory.setItem(53, icon(Material.HOPPER, line(NamedTextColor.GREEN, "gui-deposit-hand", "손에 든 아이템 입고"),
                List.of(gray("gui-deposit-hand-lore", "손에 든 아이템 묶음을 저장고에 넣습니다."))));
        holder.action(52, "storage_page", String.valueOf(Math.min(maxPage, safePage + 1)));
        inventory.setItem(52, icon(Material.ARROW, line(NamedTextColor.YELLOW, "gui-next-page", "다음 페이지"),
                List.of(gray("gui-page-of", "페이지 {page}/{pages}", Map.of(
                        "page", String.valueOf(safePage + 1),
                        "pages", String.valueOf(maxPage + 1))))));
        int slot = 0;
        int start = safePage * pageSize;
        int end = Math.min(entries.size(), start + pageSize);
        for (Map.Entry<String, Long> entry : entries.subList(start, end)) {
            ItemDefinition item = items.get(entry.getKey()).orElse(new ItemDefinition(
                    entry.getKey(), Material.PAPER, entry.getKey(), 0, false, 0, false, List.of()));
            ItemStack stack = new ItemStack(item.material(), (int) Math.max(1, Math.min(64, entry.getValue())));
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(label(item.displayName(), NamedTextColor.WHITE));
            meta.lore(List.of(gray("gui-amount", "수량: {amount}", Map.of("amount", String.valueOf(entry.getValue()))),
                    line(NamedTextColor.DARK_GRAY, "gui-click-hint-stack", "좌클릭: 64, 우클릭: 1, Shift: 최대")));
            stack.setItemMeta(meta);
            holder.action(slot, "withdraw_storage", entry.getKey());
            inventory.setItem(slot++, stack);
        }
        player.openInventory(inventory);
    }

    public void openMachine(Player player, MachineInstance machine) {
        if (!requireGui(player)) {
            return;
        }
        if (!enabled("machines")) {
            messages.send(player, "feature-disabled", Map.of("feature", "machines"));
            return;
        }
        FactoryGuiHolder holder = new FactoryGuiHolder("machine", machine.islandUuid(), machine.machineId());
        Inventory inventory = Bukkit.createInventory(holder, 27, title("machine-title", "기계"));
        holder.inventory(inventory);
        MachineDefinition definition = definitions.get(machine.typeId()).orElse(null);
        List<Component> lore = new ArrayList<>();
        lore.add(gray("gui-type", "유형: {type}", Map.of("type", machine.typeId())));
        lore.add(label(messages.rawPlain("machine-status", Map.of("status", machine.status().name())), NamedTextColor.GRAY));
        lore.add(gray("gui-wear", "마모도: {wear}", Map.of("wear", NumberFormatter.ratio(machine.wear()))));
        lore.add(gray("gui-island", "섬: {island}", Map.of("island", machine.islandUuid().toString())));
        if (definition != null) {
            lore.add(gray("gui-machine-power", "전력: {power}", Map.of("power", String.valueOf(definition.powerConsumption()))));
            lore.add(gray("gui-tier", "티어: {tier}", Map.of("tier", String.valueOf(definition.tier()))));
            if (!definition.requiredUnlocks().isEmpty()) {
                lore.add(gray("gui-requires", "필요 조건: {requires}", Map.of("requires", definition.requiredUnlocks().toString())));
            }
            if (definition.isLogistics()) {
                lore.add(gray("gui-throughput", "처리량: {throughput}/cycle", Map.of("throughput", String.valueOf(definition.logisticsThroughput()))));
            }
        }
        storage.get(machine.inputInventoryId()).ifPresent(input -> {
            lore.add(gray("gui-input", "입력: {used}/{capacity}", Map.of(
                    "used", String.valueOf(input.used()),
                    "capacity", String.valueOf(input.capacity()))));
            if (!input.items().isEmpty()) {
                lore.add(line(NamedTextColor.DARK_GRAY, "gui-input-items", "입력품: {items}", Map.of("items", input.items().toString())));
            }
        });
        storage.get(machine.outputInventoryId()).ifPresent(output -> {
            lore.add(gray("gui-output", "출력: {used}/{capacity}", Map.of(
                    "used", String.valueOf(output.used()),
                    "capacity", String.valueOf(output.capacity()))));
            if (!output.items().isEmpty()) {
                lore.add(line(NamedTextColor.DARK_GRAY, "gui-output-items", "출력품: {items}", Map.of("items", output.items().toString())));
            }
        });
        if (machine.linkedResourceNodeId() != null) {
            lore.add(gray("gui-node", "노드: {node}", Map.of("node", machine.linkedResourceNodeId().toString())));
        }
        ItemStack info = new ItemStack(definition == null ? Material.STONE : definition.material());
        ItemMeta meta = info.getItemMeta();
        meta.displayName(label(definition == null ? machine.typeId() : definition.displayName(), NamedTextColor.GOLD));
        meta.lore(lore);
        info.setItemMeta(meta);
        inventory.setItem(13, info);
        addRecipeSelectors(holder, inventory, machine, definition);
        holder.action(20, "deposit_machine_input", "");
        inventory.setItem(20, icon(Material.HOPPER, line(NamedTextColor.GREEN, "gui-deposit-input", "입력 입고"),
                List.of(gray("gui-deposit-input-lore", "손에 든 아이템 묶음을 이 기계 입력 저장소에 넣습니다."))));
        holder.action(22, "withdraw_machine_input", "");
        inventory.setItem(22, icon(Material.DROPPER, line(NamedTextColor.YELLOW, "gui-take-input", "입력 출고"),
                List.of(gray("gui-take-input-lore", "이 기계 입력 저장소에서 최대 한 묶음을 꺼냅니다."))));
        holder.action(24, "withdraw_machine_output", "");
        inventory.setItem(24, icon(Material.CHEST, line(NamedTextColor.AQUA, "gui-take-output", "출력 출고"),
                List.of(gray("gui-take-output-lore", "이 기계 출력 저장소에서 최대 한 묶음을 꺼냅니다."))));
        holder.action(26, "reclaim_machine", "");
        inventory.setItem(26, icon(Material.BARRIER, line(NamedTextColor.RED, "gui-reclaim-machine", "기계 회수"),
                List.of(gray("gui-reclaim-machine-lore", "버퍼를 섬 저장고로 돌려보내고 기계를 회수합니다."))));
        player.openInventory(inventory);
    }

    private void addRecipeSelectors(FactoryGuiHolder holder, Inventory inventory, MachineInstance machine, MachineDefinition definition) {
        if (definition == null) {
            return;
        }
        FactoryIsland island = islands.find(machine.islandUuid()).orElse(null);
        if (island == null) {
            return;
        }
        List<RecipeDefinition> availableRecipes = recipes.recipesFor(machine.typeId()).stream()
                .filter(recipe -> definition.allowedRecipes().isEmpty() || definition.allowedRecipes().contains(recipe.id()))
                .filter(recipe -> recipe.minTier() <= island.tier())
                .filter(recipe -> recipe.researchRequired().isEmpty() || research.unlocked(island).containsAll(recipe.researchRequired()))
                .toList();
        if (availableRecipes.isEmpty()) {
            return;
        }
        String selectedRecipeId = machine.selectedRecipeId();
        holder.action(0, "select_recipe", "");
        inventory.setItem(0, icon(selectedRecipeId == null || selectedRecipeId.isBlank() ? Material.LIME_DYE : Material.GRAY_DYE,
                line(NamedTextColor.AQUA, "gui-auto-recipe", "자동 레시피"),
                List.of(gray("gui-auto-recipe-lore", "실행 가능한 첫 레시피를 기계가 선택합니다."))));
        int slot = 1;
        for (RecipeDefinition recipe : availableRecipes) {
            if (slot >= 9) {
                break;
            }
            boolean selected = recipe.id().equals(selectedRecipeId);
            holder.action(slot, "select_recipe", recipe.id());
            inventory.setItem(slot++, icon(selected ? Material.LIME_DYE : Material.PAPER,
                    label(recipe.id(), selected ? NamedTextColor.GREEN : NamedTextColor.YELLOW),
                    List.of(gray("gui-recipe-input", "입력: {input}", Map.of("input", recipe.input().toString())),
                            gray("gui-recipe-output", "출력: {output}", Map.of("output", recipe.output().toString())),
                            gray("gui-recipe-byproducts", "부산물: {byproducts}", Map.of("byproducts", recipe.byproducts().toString())))));
        }
    }

    public void openContracts(Player player, FactoryIsland island, ContractService contracts) {
        if (!requireGui(player)) {
            return;
        }
        if (!enabled("contracts")) {
            messages.send(player, "feature-disabled", Map.of("feature", "contracts"));
            player.closeInventory();
            return;
        }
        if (!enabled("storage")) {
            messages.send(player, "feature-disabled", Map.of("feature", "storage"));
            player.closeInventory();
            return;
        }
        FactoryGuiHolder holder = new FactoryGuiHolder("contracts", island.islandUuid(), null);
        Inventory inventory = Bukkit.createInventory(holder, 27, title("contracts-title", "공장 계약"));
        holder.inventory(inventory);
        int slot = 10;
        List<ContractService.ActiveContract> activeContracts = contracts.activeContracts(island);
        inventory.setItem(4, icon(Material.CLOCK, line(NamedTextColor.AQUA, "gui-active-contracts", "진행 중인 계약"),
                List.of(gray("gui-open-count", "진행 중: {count}", Map.of("count", String.valueOf(activeContracts.size()))))));
        for (ContractService.ActiveContract active : activeContracts) {
            if (slot >= 17) {
                break;
            }
            ContractTemplate template = active.template();
            holder.action(slot, "contract_detail", active.contractId().toString());
            inventory.setItem(slot++, icon(Material.WRITABLE_BOOK, label(template.id(), NamedTextColor.GOLD),
                    List.of(gray("gui-type", "유형: {type}", Map.of("type", template.type())),
                            gray("gui-tier", "티어: {tier}", Map.of("tier", String.valueOf(template.tier()))),
                            gray("gui-required", "필요: {required}", Map.of("required", template.required().toString())),
                            gray("gui-money", "돈: {money}", Map.of("money", String.valueOf(template.money()))),
                            gray("gui-research-value", "연구: {research}", Map.of("research", String.valueOf(template.research()))),
                            gray("gui-reputation-value", "평판: {reputation}", Map.of("reputation", String.valueOf(template.reputation()))),
                            gray("gui-items", "아이템: {items}", Map.of("items", template.itemRewards().toString())),
                            gray("gui-expires", "만료: {minutes}분", Map.of("minutes", String.valueOf(NumberFormatter.minutesUntil(active.expiresAt(), System.currentTimeMillis())))))));
        }
        if (enabled("maintenance")) {
            contracts.emergencyTemplate().ifPresent(template -> {
                int used = contracts.emergencyUsedToday(island);
                boolean available = island.maintenanceDebt() > 0 && used < contracts.emergencyDailyLimit();
                if (available) {
                    holder.action(22, "complete_emergency", "");
                }
                inventory.setItem(22, icon(available ? Material.FIREWORK_STAR : Material.GRAY_DYE,
                        line(available ? NamedTextColor.RED : NamedTextColor.GRAY, "gui-emergency-contract", "비상 계약"),
                        List.of(gray("gui-debt", "부채: {debt}", Map.of("debt", String.valueOf(island.maintenanceDebt()))),
                                gray("gui-used-today", "오늘 사용: {used}/{limit}", Map.of(
                                        "used", String.valueOf(used),
                                        "limit", String.valueOf(contracts.emergencyDailyLimit()))),
                                gray("gui-required", "필요: {required}", Map.of("required", template.required().toString())),
                                gray("gui-debt-relief", "부채 상환: {amount}", Map.of("amount", String.valueOf(template.debtRelief()))),
                                gray(available ? "gui-emergency-deliver" : "gui-emergency-unavailable",
                                        available ? "섬 저장고에서 납품합니다." : "사용 가능한 비상 납품이 없습니다."))));
            });
        }
        player.openInventory(inventory);
    }

    public void openContractDetail(Player player, FactoryIsland island, ContractService contracts, java.util.UUID contractId) {
        if (!requireGui(player)) {
            return;
        }
        if (!enabled("contracts")) {
            messages.send(player, "feature-disabled", Map.of("feature", "contracts"));
            player.closeInventory();
            return;
        }
        if (!enabled("storage")) {
            messages.send(player, "feature-disabled", Map.of("feature", "storage"));
            player.closeInventory();
            return;
        }
        ContractService.ActiveContract active = contracts.activeContracts(island).stream()
                .filter(contract -> contract.contractId().equals(contractId))
                .findFirst()
                .orElse(null);
        if (active == null) {
            openContracts(player, island, contracts);
            return;
        }
        ContractTemplate template = active.template();
        FactoryGuiHolder holder = new FactoryGuiHolder("contract-detail", island.islandUuid(), null);
        Inventory inventory = Bukkit.createInventory(holder, 27, title("contract-detail-title", "계약 상세"));
        holder.inventory(inventory);
        inventory.setItem(4, icon(Material.WRITABLE_BOOK, label(template.id(), NamedTextColor.GOLD),
                List.of(gray("gui-type", "유형: {type}", Map.of("type", template.type())),
                        gray("gui-tier", "티어: {tier}", Map.of("tier", String.valueOf(template.tier()))),
                        gray("gui-expires", "만료: {minutes}분", Map.of("minutes", String.valueOf(NumberFormatter.minutesUntil(active.expiresAt(), System.currentTimeMillis())))))));
        inventory.setItem(11, icon(Material.CHEST, line(NamedTextColor.YELLOW, "gui-required-items", "필요 아이템"),
                contractLines(template.required())));
        inventory.setItem(15, icon(Material.EMERALD, line(NamedTextColor.GREEN, "gui-rewards", "보상"),
                rewardLines(template)));
        holder.action(18, "contracts_back", "");
        inventory.setItem(18, icon(Material.ARROW, line(NamedTextColor.YELLOW, "gui-back", "뒤로"),
                List.of(gray("gui-back-contracts", "계약 목록으로 돌아갑니다."))));
        holder.action(22, "complete_contract", active.contractId().toString());
        inventory.setItem(22, icon(Material.LIME_DYE, line(NamedTextColor.GREEN, "gui-deliver-contract", "계약 납품"),
                List.of(gray("gui-deliver-contract-lore", "섬 저장고에서 필요 아이템을 제출합니다."))));
        player.openInventory(inventory);
    }

    private List<Component> contractLines(Map<String, Long> values) {
        if (values.isEmpty()) {
            return List.of(gray("gui-no-items-required", "필요 아이템 없음"));
        }
        return values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> gray("gui-item-amount", "{item} x{amount}", Map.of(
                        "item", entry.getKey(),
                        "amount", String.valueOf(entry.getValue()))))
                .toList();
    }

    private List<Component> rewardLines(ContractTemplate template) {
        List<Component> lore = new ArrayList<>();
        if (template.money() > 0) {
            lore.add(gray("gui-money", "돈: {money}", Map.of("money", String.valueOf(template.money()))));
        }
        if (template.research() > 0) {
            lore.add(gray("gui-research-value", "연구: {research}", Map.of("research", String.valueOf(template.research()))));
        }
        if (template.reputation() > 0) {
            lore.add(gray("gui-reputation-value", "평판: {reputation}", Map.of("reputation", String.valueOf(template.reputation()))));
        }
        if (enabled("maintenance") && template.debtRelief() > 0) {
            lore.add(gray("gui-debt-relief", "부채 상환: {amount}", Map.of("amount", String.valueOf(template.debtRelief()))));
        }
        template.itemRewards().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> gray("gui-item-amount", "{item} x{amount}", Map.of(
                        "item", entry.getKey(),
                        "amount", String.valueOf(entry.getValue()))))
                .forEach(lore::add);
        return lore.isEmpty() ? List.of(gray("gui-no-rewards", "보상 없음")) : lore;
    }

    public void openMarket(Player player, FactoryIsland island, MarketService market) {
        openMarket(player, island, market, 0);
    }

    public void openMarket(Player player, FactoryIsland island, MarketService market, int page) {
        if (!requireGui(player)) {
            return;
        }
        if (!enabled("market")) {
            messages.send(player, "feature-disabled", Map.of("feature", "market"));
            player.closeInventory();
            return;
        }
        if (!enabled("storage")) {
            messages.send(player, "feature-disabled", Map.of("feature", "storage"));
            player.closeInventory();
            return;
        }
        int safePage = Math.max(0, page);
        FactoryGuiHolder holder = new FactoryGuiHolder("market", island.islandUuid(), null, safePage);
        Inventory inventory = Bukkit.createInventory(holder, 54, title("market-title", "공장 시장"));
        holder.inventory(inventory);
        List<String> itemIds = market.prices().keySet().stream().sorted().toList();
        int pageSize = 45;
        int maxPage = Math.max(0, (itemIds.size() - 1) / pageSize);
        if (safePage > maxPage) {
            openMarket(player, island, market, maxPage);
            return;
        }
        holder.action(45, "market_page", String.valueOf(Math.max(0, safePage - 1)));
        inventory.setItem(45, icon(Material.ARROW, line(NamedTextColor.YELLOW, "gui-previous-page", "이전 페이지"),
                List.of(gray("gui-page-of", "페이지 {page}/{pages}", Map.of(
                        "page", String.valueOf(safePage + 1),
                        "pages", String.valueOf(maxPage + 1))))));
        inventory.setItem(49, icon(Material.EMERALD, line(NamedTextColor.GREEN, "gui-market", "시장"),
                marketLore(island, safePage, maxPage)));
        holder.action(52, "market_page", String.valueOf(Math.min(maxPage, safePage + 1)));
        inventory.setItem(52, icon(Material.ARROW, line(NamedTextColor.YELLOW, "gui-next-page", "다음 페이지"),
                List.of(gray("gui-page-of", "페이지 {page}/{pages}", Map.of(
                        "page", String.valueOf(safePage + 1),
                        "pages", String.valueOf(maxPage + 1))))));
        int start = safePage * pageSize;
        int end = Math.min(itemIds.size(), start + pageSize);
        int slot = 0;
        VirtualInventory virtual = storage.islandStorageIfAllowed(island.islandUuid()).orElse(null);
        for (String itemId : itemIds.subList(start, end)) {
            ItemDefinition item = items.get(itemId).orElse(new ItemDefinition(
                    itemId, Material.PAPER, itemId, 0, false, market.prices().getOrDefault(itemId, 0L), false, List.of()));
            long stored = virtual == null ? 0 : virtual.amount(itemId);
            long unitPrice = market.price(island.islandUuid(), itemId, 1);
            ItemStack stack = new ItemStack(item.material(), (int) Math.max(1, Math.min(64, stored)));
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(label(item.displayName(), stored > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            meta.lore(List.of(gray("gui-stored", "보유량: {stored}", Map.of("stored", String.valueOf(stored))),
                    gray("gui-current-price", "현재 가격: {price}", Map.of("price", String.valueOf(unitPrice))),
                    line(NamedTextColor.DARK_GRAY, "gui-click-hint-stack", "좌클릭: 64, 우클릭: 1, Shift: 최대")));
            stack.setItemMeta(meta);
            holder.action(slot, "sell_market_item", itemId);
            inventory.setItem(slot++, stack);
        }
        player.openInventory(inventory);
    }

    public void openResearch(Player player, FactoryIsland island, ResearchService research) {
        if (!requireGui(player)) {
            return;
        }
        if (!enabled("research")) {
            messages.send(player, "feature-disabled", Map.of("feature", "research"));
            player.closeInventory();
            return;
        }
        FactoryGuiHolder holder = new FactoryGuiHolder("research", island.islandUuid(), null);
        Inventory inventory = Bukkit.createInventory(holder, 27, title("research-title", "공장 연구"));
        holder.inventory(inventory);
        int slot = 10;
        Set<String> unlockedIds = research.unlocked(island);
        double balance = economy.balance(player);
        for (UnlockDefinition unlock : research.all().values()) {
            if (slot >= 17) {
                break;
            }
            boolean unlocked = unlockedIds.contains(unlock.id());
            boolean hasRequiredUnlocks = unlockedIds.containsAll(unlock.requires());
            boolean hasPoints = island.researchPoints() >= unlock.cost();
            boolean hasReputation = island.reputation() >= unlock.requiredReputation();
            boolean hasMoney = balance >= unlock.moneyCost();
            boolean ready = !unlocked && hasRequiredUnlocks && hasPoints && hasReputation && hasMoney;
            String prerequisiteStatus = text(hasRequiredUnlocks ? "gui-ready" : "gui-missing",
                    hasRequiredUnlocks ? "준비됨" : "부족");
            String unlockStatus = text(unlocked ? "gui-unlocked" : (ready ? "gui-ready" : "gui-locked"),
                    unlocked ? "해금됨" : (ready ? "준비됨" : "잠김"));
            holder.action(slot, "unlock_research", unlock.id());
            inventory.setItem(slot++, icon(unlocked ? Material.LIME_DYE : (ready ? Material.YELLOW_DYE : Material.GRAY_DYE),
                    label(unlock.displayName(), unlocked ? NamedTextColor.GREEN : NamedTextColor.YELLOW),
                    List.of(gray("gui-research-progress", "연구: {current}/{required}", Map.of(
                                    "current", String.valueOf(island.researchPoints()),
                                    "required", String.valueOf(unlock.cost()))),
                            gray("gui-money-progress", "돈: {current}/{required}", Map.of(
                                    "current", NumberFormatter.whole(balance),
                                    "required", String.valueOf(unlock.moneyCost()))),
                            gray("gui-reputation-progress", "평판: {current}/{required}", Map.of(
                                    "current", String.valueOf(island.reputation()),
                                    "required", String.valueOf(unlock.requiredReputation()))),
                            gray("gui-id", "식별자: {id}", Map.of("id", unlock.id())),
                            gray("gui-requires", "필요 조건: {requires}", Map.of("requires", unlock.requires().toString())),
                            gray("gui-unlocks", "해금: {unlocks}", Map.of("unlocks", unlock.grants().toString())),
                            gray("gui-factory-tier", "공장 티어: {tier}", Map.of("tier", unlock.factoryTier() > 0 ? String.valueOf(unlock.factoryTier()) : "-")),
                            gray("gui-prerequisites", "선행 조건: {status}", Map.of("status", prerequisiteStatus)),
                            gray("gui-status", "상태: {status}", Map.of("status", unlockStatus)))));
        }
        inventory.setItem(22, icon(Material.EXPERIENCE_BOTTLE, line(NamedTextColor.AQUA, "gui-research-points", "연구 포인트"),
                List.of(gray("gui-points", "포인트: {points}", Map.of("points", String.valueOf(island.researchPoints()))))));
        player.openInventory(inventory);
    }

    private ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private Component title(String key, String fallback) {
        return label(text(key, fallback), NamedTextColor.DARK_GRAY);
    }

    private Component gray(String key, String fallback) {
        return line(NamedTextColor.GRAY, key, fallback);
    }

    private Component gray(String key, String fallback, Map<String, String> placeholders) {
        return line(NamedTextColor.GRAY, key, fallback, placeholders);
    }

    private Component line(NamedTextColor color, String key, String fallback) {
        return label(text(key, fallback), color);
    }

    private Component line(NamedTextColor color, String key, String fallback, Map<String, String> placeholders) {
        return label(text(key, fallback, placeholders), color);
    }

    private Component label(String value, NamedTextColor color) {
        return Component.text(value == null ? "" : value, color).decoration(TextDecoration.ITALIC, false);
    }

    private String text(String key, String fallback) {
        String value = messages.rawPlain(key);
        return value.equals(key) ? fallback : value;
    }

    private String text(String key, String fallback, Map<String, String> placeholders) {
        String value = messages.rawPlain(key, placeholders);
        return value.equals(key) ? replace(fallback, placeholders) : value;
    }

    private String replace(String text, Map<String, String> placeholders) {
        String replaced = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            replaced = replaced.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return replaced;
    }
}
