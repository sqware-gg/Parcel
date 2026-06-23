package com.sqware.parcel;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class StoreGui implements Listener {
    private static final int MAX_ACTION_COMMAND_LENGTH = 512;
    private static final boolean HAS_MODERN_MATERIALS = Material.matchMaterial("GRAY_STAINED_GLASS_PANE") != null;

    private final ParcelPlugin plugin;
    private volatile StoreConfig config;

    StoreGui(ParcelPlugin plugin) {
        this.plugin = plugin;
    }

    void reload(StoreConfig config) {
        this.config = config;
    }

    boolean open(Player player) {
        StoreConfig activeConfig = config;
        if (activeConfig == null || !activeConfig.enabled() || !activeConfig.gui().enabled()) {
            return false;
        }

        StoreMenuHolder holder = new StoreMenuHolder();
        Inventory inventory = Bukkit.createInventory(
                holder,
                activeConfig.gui().size(),
                color(render(activeConfig.gui().title(), player, activeConfig))
        );
        holder.setInventory(inventory);

        StoreConfig.Fill fill = activeConfig.gui().fill();
        if (fill.enabled()) {
            ItemStack filler = createFillItem(fill);
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }

        for (StoreConfig.Item item : activeConfig.gui().items()) {
            ItemStack stack = createDisplayItem(item, player, activeConfig);
            for (Integer slot : item.slots()) {
                inventory.setItem(slot, stack);
                holder.setItem(slot, item);
            }
        }

        player.openInventory(inventory);
        return true;
    }

    void sendWebstoreLink(CommandSender sender) {
        StoreConfig activeConfig = config;
        if (activeConfig == null || !activeConfig.hasWebstoreUrl()) {
            plugin.message(sender, activeConfig == null
                    ? "&cThe webstore URL is not configured."
                    : activeConfig.notConfiguredMessage());
            return;
        }

        for (String line : activeConfig.webstoreMessage()) {
            plugin.message(sender, render(line, sender, activeConfig));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        StoreMenuHolder holder = holder(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        StoreConfig.Item item = holder.item(rawSlot);
        if (item == null) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (!hasItemPermission(player, item)) {
            StoreConfig activeConfig = config;
            plugin.message(player, activeConfig == null ? "&cNo permission." : activeConfig.noPermissionMessage());
            return;
        }

        executeActions(player, item);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (holder(event.getView().getTopInventory()) != null) {
            event.setCancelled(true);
        }
    }

    private StoreMenuHolder holder(Inventory inventory) {
        if (inventory == null || !(inventory.getHolder() instanceof StoreMenuHolder)) {
            return null;
        }
        return (StoreMenuHolder) inventory.getHolder();
    }

    private boolean hasItemPermission(Player player, StoreConfig.Item item) {
        return isBlank(item.permission()) || player.hasPermission(item.permission()) || player.hasPermission("parcel.admin");
    }

    private void executeActions(Player player, StoreConfig.Item item) {
        StoreConfig activeConfig = config;
        if (activeConfig == null) {
            return;
        }

        for (String rawAction : item.actions()) {
            String action = rawAction == null ? "" : rawAction.trim();
            if (isBlank(action)) {
                continue;
            }

            String lower = action.toLowerCase(Locale.ROOT);
            if (lower.equals("close")) {
                player.closeInventory();
                continue;
            }
            if (lower.equals("webstore") || lower.equals("link") || lower.equals("url")) {
                sendWebstoreLink(player);
                continue;
            }
            if (lower.equals("store") || lower.equals("open")) {
                open(player);
                continue;
            }
            if (lower.startsWith("message:")) {
                plugin.message(player, render(action.substring("message:".length()), player, activeConfig));
                continue;
            }
            if (lower.startsWith("player:")) {
                dispatchPlayerCommand(player, action.substring("player:".length()), activeConfig);
                continue;
            }
            if (lower.startsWith("console:")) {
                dispatchConsoleCommand(player, action.substring("console:".length()), activeConfig);
                continue;
            }
            if (lower.startsWith("command:")) {
                dispatchConsoleCommand(player, action.substring("command:".length()), activeConfig);
                continue;
            }

            plugin.getLogger().warning("Unknown store action on item '" + item.id() + "': " + action);
        }
    }

    private void dispatchPlayerCommand(Player player, String command, StoreConfig activeConfig) {
        String parsed = normalizeCommand(render(command, player, activeConfig));
        if (!isSafeCommand(parsed)) {
            plugin.getLogger().warning("Blocked unsafe player store command for " + player.getName() + ".");
            return;
        }
        player.performCommand(parsed);
    }

    private void dispatchConsoleCommand(Player player, String command, StoreConfig activeConfig) {
        String parsed = normalizeCommand(render(command, player, activeConfig));
        if (!isSafeCommand(parsed)) {
            plugin.getLogger().warning("Blocked unsafe console store command for " + player.getName() + ".");
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
    }

    private ItemStack createFillItem(StoreConfig.Fill fill) {
        ItemStack stack = createItemStack(fill.material(), 1, fill.data());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(fill.name()));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack createDisplayItem(StoreConfig.Item item, Player player, StoreConfig activeConfig) {
        ItemStack stack = createItemStack(item.material(), item.amount(), item.data());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (!isBlank(item.name())) {
                meta.setDisplayName(color(render(item.name(), player, activeConfig)));
            }
            if (!item.lore().isEmpty()) {
                List<String> lore = new ArrayList<>();
                for (String line : item.lore()) {
                    lore.add(color(render(line, player, activeConfig)));
                }
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack createItemStack(Material material, int amount, short data) {
        if (data == 0 || (HAS_MODERN_MATERIALS && !material.name().startsWith("LEGACY_"))) {
            return new ItemStack(material, amount);
        }
        return new ItemStack(material, amount, data);
    }

    private String render(String value, CommandSender sender, StoreConfig activeConfig) {
        String rendered = value == null ? "" : value;
        rendered = rendered.replace("{webstore_url}", activeConfig.webstoreUrl());
        rendered = rendered.replace("{player}", sender instanceof Player ? ((Player) sender).getName() : "console");
        rendered = rendered.replace("{uuid}", sender instanceof Player
                ? ((Player) sender).getUniqueId().toString()
                : "");
        return rendered;
    }

    private String normalizeCommand(String command) {
        String parsed = command == null ? "" : command.trim();
        while (parsed.startsWith("/")) {
            parsed = parsed.substring(1).trim();
        }
        return parsed;
    }

    private boolean isSafeCommand(String command) {
        if (isBlank(command) || command.length() > MAX_ACTION_COMMAND_LENGTH) {
            return false;
        }
        for (int i = 0; i < command.length(); i++) {
            if (Character.isISOControl(command.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class StoreMenuHolder implements InventoryHolder {
        private final Map<Integer, StoreConfig.Item> items = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private void setItem(int slot, StoreConfig.Item item) {
            items.put(slot, item);
        }

        private StoreConfig.Item item(int slot) {
            return items.get(slot);
        }
    }
}
