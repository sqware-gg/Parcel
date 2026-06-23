package com.sqware.parcel;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class StoreConfig {
    static final String DEFAULT_PERMISSION = "parcel.store";

    private static final String DEFAULT_WEBSTORE_URL = "https://sqware.gg";
    private static final String DEFAULT_FILL_MATERIAL = "GRAY_STAINED_GLASS_PANE";
    private static final String LEGACY_FILL_MATERIAL = "STAINED_GLASS_PANE";
    private static final String DEFAULT_ITEM_MATERIAL = "EMERALD";
    private static final String SAFE_FALLBACK_MATERIAL = "STONE";
    private static final int DEFAULT_ROWS = 3;
    private static final int MAX_ROWS = 6;
    private static final int MAX_ITEM_AMOUNT = 64;

    private final boolean enabled;
    private final String permission;
    private final String webstoreUrl;
    private final List<String> webstoreMessage;
    private final String noPermissionMessage;
    private final String playersOnlyMessage;
    private final String disabledMessage;
    private final String notConfiguredMessage;
    private final Gui gui;

    private StoreConfig(
            boolean enabled,
            String permission,
            String webstoreUrl,
            List<String> webstoreMessage,
            String noPermissionMessage,
            String playersOnlyMessage,
            String disabledMessage,
            String notConfiguredMessage,
            Gui gui
    ) {
        this.enabled = enabled;
        this.permission = permission;
        this.webstoreUrl = webstoreUrl;
        this.webstoreMessage = webstoreMessage;
        this.noPermissionMessage = noPermissionMessage;
        this.playersOnlyMessage = playersOnlyMessage;
        this.disabledMessage = disabledMessage;
        this.notConfiguredMessage = notConfiguredMessage;
        this.gui = gui;
    }

    static StoreConfig from(JavaPlugin plugin, FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("store");
        return new StoreConfig(
                bool(section, "enabled", true),
                string(section, "permission", DEFAULT_PERMISSION),
                string(section, "webstore-url", DEFAULT_WEBSTORE_URL),
                stringList(section, "webstore-message", defaultWebstoreMessage()),
                string(section, "no-permission-message", "&cNo permission."),
                string(section, "players-only-message", "&cOnly players can open the store menu."),
                string(section, "disabled-message", "&cThe store is currently unavailable."),
                string(section, "not-configured-message", "&cThe webstore URL is not configured."),
                Gui.from(plugin, section == null ? null : section.getConfigurationSection("gui"))
        );
    }

    boolean enabled() {
        return enabled;
    }

    String permission() {
        return permission;
    }

    String webstoreUrl() {
        return webstoreUrl;
    }

    List<String> webstoreMessage() {
        return webstoreMessage;
    }

    String noPermissionMessage() {
        return noPermissionMessage;
    }

    String playersOnlyMessage() {
        return playersOnlyMessage;
    }

    String disabledMessage() {
        return disabledMessage;
    }

    String notConfiguredMessage() {
        return notConfiguredMessage;
    }

    Gui gui() {
        return gui;
    }

    boolean hasWebstoreUrl() {
        return !isBlank(webstoreUrl);
    }

    static final class Gui {
        private final boolean enabled;
        private final String title;
        private final int rows;
        private final Fill fill;
        private final List<Item> items;

        private Gui(boolean enabled, String title, int rows, Fill fill, List<Item> items) {
            this.enabled = enabled;
            this.title = title;
            this.rows = rows;
            this.fill = fill;
            this.items = items;
        }

        private static Gui from(JavaPlugin plugin, ConfigurationSection section) {
            int rows = clampRows(section == null ? DEFAULT_ROWS : section.getInt("rows", DEFAULT_ROWS));
            int size = rows * 9;
            return new Gui(
                    bool(section, "enabled", true),
                    string(section, "title", "&bServer Store"),
                    rows,
                    Fill.from(plugin, section == null ? null : section.getConfigurationSection("fill")),
                    parseItems(plugin, section == null ? null : section.getConfigurationSection("items"), size)
            );
        }

        boolean enabled() {
            return enabled;
        }

        String title() {
            return title;
        }

        int size() {
            return rows * 9;
        }

        Fill fill() {
            return fill;
        }

        List<Item> items() {
            return items;
        }
    }

    static final class Fill {
        private final boolean enabled;
        private final Material material;
        private final short data;
        private final String name;

        private Fill(boolean enabled, Material material, short data, String name) {
            this.enabled = enabled;
            this.material = material;
            this.data = data;
            this.name = name;
        }

        private static Fill from(JavaPlugin plugin, ConfigurationSection section) {
            return new Fill(
                    bool(section, "enabled", true),
                    StoreConfig.material(
                            plugin,
                            "store.gui.fill.material",
                            string(section, "material", DEFAULT_FILL_MATERIAL),
                            DEFAULT_FILL_MATERIAL,
                            LEGACY_FILL_MATERIAL,
                            SAFE_FALLBACK_MATERIAL
                    ),
                    StoreConfig.data(section),
                    string(section, "name", " ")
            );
        }

        boolean enabled() {
            return enabled;
        }

        Material material() {
            return material;
        }

        short data() {
            return data;
        }

        String name() {
            return name;
        }
    }

    static final class Item {
        private final String id;
        private final List<Integer> slots;
        private final Material material;
        private final int amount;
        private final short data;
        private final String name;
        private final List<String> lore;
        private final String permission;
        private final List<String> actions;

        private Item(
                String id,
                List<Integer> slots,
                Material material,
                int amount,
                short data,
                String name,
                List<String> lore,
                String permission,
                List<String> actions
        ) {
            this.id = id;
            this.slots = slots;
            this.material = material;
            this.amount = amount;
            this.data = data;
            this.name = name;
            this.lore = lore;
            this.permission = permission;
            this.actions = actions;
        }

        String id() {
            return id;
        }

        List<Integer> slots() {
            return slots;
        }

        Material material() {
            return material;
        }

        int amount() {
            return amount;
        }

        short data() {
            return data;
        }

        String name() {
            return name;
        }

        List<String> lore() {
            return lore;
        }

        String permission() {
            return permission;
        }

        List<String> actions() {
            return actions;
        }
    }

    private static List<Item> parseItems(JavaPlugin plugin, ConfigurationSection section, int inventorySize) {
        if (section == null) {
            return defaultItems();
        }

        List<Item> items = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(id);
            if (itemSection == null) {
                plugin.getLogger().warning("Ignoring store item '" + id + "' because it is not a config section.");
                continue;
            }

            List<Integer> slots = slots(plugin, "store.gui.items." + id, itemSection, inventorySize);
            if (slots.isEmpty()) {
                continue;
            }

            List<String> actions = stringList(itemSection, "actions", Collections.singletonList("webstore"));
            items.add(new Item(
                    id,
                    slots,
                    material(
                            plugin,
                            "store.gui.items." + id + ".material",
                            string(itemSection, "material", DEFAULT_ITEM_MATERIAL),
                            DEFAULT_ITEM_MATERIAL,
                            SAFE_FALLBACK_MATERIAL
                    ),
                    clampAmount(itemSection.getInt("amount", 1)),
                    data(itemSection),
                    string(itemSection, "name", "&bOpen Webstore"),
                    stringList(itemSection, "lore", Collections.<String>emptyList()),
                    string(itemSection, "permission", ""),
                    actions
            ));
        }
        return Collections.unmodifiableList(items);
    }

    private static List<Item> defaultItems() {
        List<Integer> slots = Collections.singletonList(13);
        List<String> lore = new ArrayList<>();
        lore.add("&7Visit the webstore to view");
        lore.add("&7available ranks and packages.");
        lore.add("");
        lore.add("&eClick for the link.");
        List<String> actions = new ArrayList<>();
        actions.add("webstore");
        actions.add("close");
        List<Item> items = new ArrayList<>();
        items.add(new Item(
                "webstore",
                slots,
                material(null, "store.gui.items.webstore.material", DEFAULT_ITEM_MATERIAL, DEFAULT_ITEM_MATERIAL, SAFE_FALLBACK_MATERIAL),
                1,
                (short) 0,
                "&bOpen Webstore",
                Collections.unmodifiableList(lore),
                "",
                Collections.unmodifiableList(actions)
        ));
        return Collections.unmodifiableList(items);
    }

    private static List<Integer> slots(
            JavaPlugin plugin,
            String path,
            ConfigurationSection section,
            int inventorySize
    ) {
        Set<Integer> slots = new LinkedHashSet<>();
        if (section.contains("slot")) {
            slots.add(section.getInt("slot"));
        }
        if (section.isList("slots")) {
            for (Object value : section.getList("slots")) {
                Integer slot = parseSlot(value);
                if (slot != null) {
                    slots.add(slot);
                }
            }
        }

        List<Integer> validSlots = new ArrayList<>();
        for (Integer slot : slots) {
            if (slot == null || slot < 0 || slot >= inventorySize) {
                plugin.getLogger().warning("Ignoring invalid slot in " + path + ": " + slot);
                continue;
            }
            validSlots.add(slot);
        }
        if (validSlots.isEmpty()) {
            plugin.getLogger().warning("Ignoring store item at " + path + " because it has no valid slots.");
        }
        return Collections.unmodifiableList(validSlots);
    }

    private static Integer parseSlot(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Material material(JavaPlugin plugin, String path, String value, String fallback, String... additionalFallbacks) {
        String materialName = normalizeMaterialName(value);
        Material material = resolveMaterial(materialName);
        if (material != null) {
            return material;
        }

        material = resolveMaterial(fallback);
        if (material == null) {
            for (String additionalFallback : additionalFallbacks) {
                material = resolveMaterial(additionalFallback);
                if (material != null) {
                    break;
                }
            }
        }
        if (material == null) {
            throw new IllegalStateException("No compatible Bukkit material found for " + path + ".");
        }

        if (plugin != null && !isBlank(materialName)) {
            plugin.getLogger().warning("Invalid material at " + path + ": " + value + ". Using " + material.name() + ".");
        }
        return material;
    }

    private static Material resolveMaterial(String value) {
        String materialName = normalizeMaterialName(value);
        if (isBlank(materialName)) {
            return null;
        }

        for (String alias : preferredMaterialAliases(materialName)) {
            Material material = Material.matchMaterial(alias);
            if (material != null) {
                return material;
            }
        }

        Material material = Material.matchMaterial(materialName);
        if (material != null) {
            return material;
        }

        for (String alias : fallbackMaterialAliases(materialName)) {
            material = Material.matchMaterial(alias);
            if (material != null) {
                return material;
            }
        }
        return null;
    }

    private static String normalizeMaterialName(String value) {
        return value == null ? "" : value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private static String[] preferredMaterialAliases(String materialName) {
        if ("STAINED_GLASS_PANE".equals(materialName)) {
            return new String[] {"GRAY_STAINED_GLASS_PANE", "LEGACY_STAINED_GLASS_PANE"};
        }
        return new String[0];
    }

    private static String[] fallbackMaterialAliases(String materialName) {
        if ("GRAY_STAINED_GLASS_PANE".equals(materialName)) {
            return new String[] {"STAINED_GLASS_PANE", "LEGACY_STAINED_GLASS_PANE"};
        }
        return new String[0];
    }

    private static short data(ConfigurationSection section) {
        if (section == null) {
            return 0;
        }
        int value = section.getInt("data", 0);
        if (value < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        if (value > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        return (short) value;
    }

    private static int clampRows(int rows) {
        if (rows < 1) {
            return 1;
        }
        if (rows > MAX_ROWS) {
            return MAX_ROWS;
        }
        return rows;
    }

    private static int clampAmount(int amount) {
        if (amount < 1) {
            return 1;
        }
        if (amount > MAX_ITEM_AMOUNT) {
            return MAX_ITEM_AMOUNT;
        }
        return amount;
    }

    private static boolean bool(ConfigurationSection section, String path, boolean fallback) {
        return section == null ? fallback : section.getBoolean(path, fallback);
    }

    private static String string(ConfigurationSection section, String path, String fallback) {
        String value = section == null ? null : section.getString(path);
        return value == null ? fallback : value;
    }

    private static List<String> stringList(ConfigurationSection section, String path, List<String> fallback) {
        if (section == null || !section.isList(path)) {
            return Collections.unmodifiableList(new ArrayList<>(fallback));
        }
        return Collections.unmodifiableList(new ArrayList<>(section.getStringList(path)));
    }

    private static List<String> defaultWebstoreMessage() {
        return Collections.singletonList("&fWebstore: &b{webstore_url}");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
