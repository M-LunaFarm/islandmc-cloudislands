package kr.lunaf.cloudislands.paper.integration.customitem;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

public final class CustomBlockKeyService {
    private static final List<String> SUPPORTED_PLUGINS = List.of("ItemsAdder", "Oraxen", "Nexo", "CraftEngine", "Slimefun");

    private final List<Adapter> adapters;
    private final Map<String, Adapter> adaptersByPlugin;

    CustomBlockKeyService(List<Adapter> adapters) {
        List<Adapter> safeAdapters = adapters == null ? List.of() : adapters.stream()
            .filter(adapter -> adapter != null && adapter.pluginName() != null && !adapter.pluginName().isBlank())
            .toList();
        this.adapters = List.copyOf(safeAdapters);
        LinkedHashMap<String, Adapter> indexed = new LinkedHashMap<>();
        safeAdapters.forEach(adapter -> indexed.put(adapter.pluginName(), adapter));
        this.adaptersByPlugin = Map.copyOf(indexed);
    }

    public static CustomBlockKeyService discover(Server server) {
        List<Adapter> adapters = new ArrayList<>();
        if (enabled(server, "ItemsAdder")) {
            Adapter adapter = itemsAdderAdapter();
            if (adapter != null) {
                adapters.add(adapter);
            }
        }
        if (enabled(server, "Oraxen")) {
            Adapter adapter = oraxenAdapter();
            if (adapter != null) {
                adapters.add(adapter);
            }
        }
        if (enabled(server, "Nexo")) {
            Adapter adapter = nexoAdapter();
            if (adapter != null) {
                adapters.add(adapter);
            }
        }
        if (enabled(server, "CraftEngine")) {
            Adapter adapter = craftEngineAdapter();
            if (adapter != null) {
                adapters.add(adapter);
            }
        }
        if (enabled(server, "Slimefun")) {
            Adapter adapter = slimefunAdapter();
            if (adapter != null) {
                adapters.add(adapter);
            }
        }
        return new CustomBlockKeyService(adapters);
    }

    public static CustomBlockKeyService vanillaOnly() {
        return new CustomBlockKeyService(List.of());
    }

    public String blockKey(Block block) {
        if (block == null) {
            return "minecraft:air";
        }
        for (Adapter adapter : adapters) {
            String id = resolve(adapter.blockResolver(), block);
            if (!id.isBlank()) {
                return customKey(adapter.pluginName(), id);
            }
        }
        return block.getType().getKey().toString();
    }

    public String entityKey(Entity entity) {
        if (entity == null) {
            return "";
        }
        for (Adapter adapter : adapters) {
            String id = resolve(adapter.entityResolver(), entity);
            if (!id.isBlank()) {
                return customKey(adapter.pluginName(), id);
            }
        }
        return "entity:" + entity.getType().getKey();
    }

    public boolean supports(String pluginName) {
        return pluginName != null && adaptersByPlugin.containsKey(pluginName);
    }

    public Map<String, String> runtimeDetails(String pluginName) {
        Adapter adapter = adaptersByPlugin.get(pluginName);
        return Map.of(
            "adapter", adapter == null ? "unavailable" : adapter.description(),
            "blockKeyFormat", pluginName == null ? "" : pluginName.toLowerCase(Locale.ROOT) + ":<id>",
            "consumers", "block-delta,island-level-rescan,worth,level,ranking",
            "furniture", Boolean.toString(adapter != null && adapter.entityResolver() != null)
        );
    }

    public static List<String> supportedPlugins() {
        return SUPPORTED_PLUGINS;
    }

    static String customKey(String pluginName, String id) {
        String namespace = pluginName == null ? "custom" : pluginName.trim().toLowerCase(Locale.ROOT);
        String normalizedId = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        while (normalizedId.startsWith(":")) {
            normalizedId = normalizedId.substring(1);
        }
        return namespace + ":" + normalizedId;
    }

    private static Adapter itemsAdderAdapter() {
        Function<Block, String> blocks = reflectiveResolver(
            "dev.lone.itemsadder.api.CustomBlock", "byAlreadyPlaced", Block.class, Function.identity(), "getId"
        );
        return blocks == null ? null : new Adapter("ItemsAdder", blocks, null, "itemsadder-custom-block-api");
    }

    private static Adapter oraxenAdapter() {
        Function<Block, String> blocks = reflectiveResolver(
            "io.th0rgal.oraxen.api.OraxenBlocks", "getOraxenBlock", Location.class, Block::getLocation, "getItemID"
        );
        Function<Entity, String> furniture = reflectiveResolver(
            "io.th0rgal.oraxen.api.OraxenFurniture", "getFurnitureMechanic", Entity.class, Function.identity(), "getItemID"
        );
        return blocks == null ? null : new Adapter("Oraxen", blocks, furniture, "oraxen-block-and-furniture-api");
    }

    private static Adapter nexoAdapter() {
        Function<Block, String> blocks = reflectiveResolver(
            "com.nexomc.nexo.api.NexoBlocks", "customBlockMechanic", Location.class, Block::getLocation, "getItemID"
        );
        Function<Entity, String> furniture = reflectiveResolver(
            "com.nexomc.nexo.api.NexoFurniture", "furnitureMechanic", Entity.class, Function.identity(), "getItemID"
        );
        return blocks == null ? null : new Adapter("Nexo", blocks, furniture, "nexo-block-and-furniture-api");
    }

    static Adapter craftEngineAdapter() {
        Function<Block, String> blocks = reflectivePathResolver(
            "net.momirealms.craftengine.bukkit.api.CraftEngineBlocks",
            "getCustomBlockState",
            Block.class,
            Function.identity(),
            "owner",
            "value",
            "id",
            "asString"
        );
        Function<Entity, String> furniture = reflectivePathResolver(
            "net.momirealms.craftengine.bukkit.api.CraftEngineFurniture",
            "getLoadedFurnitureByMetaEntity",
            Entity.class,
            Function.identity(),
            "config",
            "id",
            "asString"
        );
        return blocks == null ? null : new Adapter("CraftEngine", blocks, furniture, "craftengine-stable-block-and-furniture-api");
    }

    static Adapter slimefunAdapter() {
        Function<Block, String> blocks = reflectiveStringResolver(
            "me.mrCookieSlime.Slimefun.api.BlockStorage", "checkID", Block.class, Function.identity()
        );
        return blocks == null ? null : new Adapter("Slimefun", blocks, null, "slimefun-block-storage-api");
    }

    private static <S, A> Function<S, String> reflectiveResolver(
        String ownerClassName,
        String lookupMethodName,
        Class<A> argumentType,
        Function<S, A> argumentMapper,
        String idMethodName
    ) {
        try {
            Class<?> owner = Class.forName(ownerClassName);
            Method lookup = owner.getMethod(lookupMethodName, argumentType);
            return source -> {
                try {
                    A argument = argumentMapper.apply(source);
                    Object value = lookup.invoke(null, argument);
                    if (value == null) {
                        return "";
                    }
                    Object id = value.getClass().getMethod(idMethodName).invoke(value);
                    return id == null ? "" : id.toString();
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    return "";
                }
            };
        } catch (ClassNotFoundException | NoSuchMethodException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static <S, A> Function<S, String> reflectiveStringResolver(
        String ownerClassName,
        String lookupMethodName,
        Class<A> argumentType,
        Function<S, A> argumentMapper
    ) {
        try {
            Class<?> owner = Class.forName(ownerClassName);
            Method lookup = owner.getMethod(lookupMethodName, argumentType);
            return source -> {
                try {
                    Object value = lookup.invoke(null, argumentMapper.apply(source));
                    return value == null ? "" : value.toString();
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    return "";
                }
            };
        } catch (ClassNotFoundException | NoSuchMethodException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static <S, A> Function<S, String> reflectivePathResolver(
        String ownerClassName,
        String lookupMethodName,
        Class<A> argumentType,
        Function<S, A> argumentMapper,
        String... path
    ) {
        try {
            Class<?> owner = Class.forName(ownerClassName);
            Method lookup = owner.getMethod(lookupMethodName, argumentType);
            List<String> methodPath = List.of(path);
            return source -> {
                try {
                    Object value = lookup.invoke(null, argumentMapper.apply(source));
                    for (String methodName : methodPath) {
                        if (value == null) {
                            return "";
                        }
                        value = value.getClass().getMethod(methodName).invoke(value);
                    }
                    return value == null ? "" : value.toString();
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    return "";
                }
            };
        } catch (ClassNotFoundException | NoSuchMethodException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static <T> String resolve(Function<T, String> resolver, T value) {
        if (resolver == null) {
            return "";
        }
        try {
            String resolved = resolver.apply(value);
            return resolved == null ? "" : resolved.trim();
        } catch (RuntimeException | LinkageError ignored) {
            return "";
        }
    }

    private static boolean enabled(Server server, String pluginName) {
        try {
            return server != null && server.getPluginManager().isPluginEnabled(pluginName);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    record Adapter(
        String pluginName,
        Function<Block, String> blockResolver,
        Function<Entity, String> entityResolver,
        String description
    ) {
    }
}
