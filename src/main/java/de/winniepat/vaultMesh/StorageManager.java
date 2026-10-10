
package de.winniepat.vaultMesh;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class StorageManager {

    public static final int GUI_SIZE = 54;
    public static final int PAGE_SIZE = 45;
    private static final int CONTROL_ROW_START = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 50;

    private final JavaPlugin plugin;
    private final NetworkRepository repository;
    private final NamespacedKey containerKey;
    private final NamespacedKey upgradeKey;

    private final Map<String, StorageNetwork> networks = new LinkedHashMap<>();
    private final Map<UUID, String> pendingLinks = new HashMap<>();
    private final Map<UUID, Boolean> pendingUnlinks = new HashMap<>();

    public StorageManager(JavaPlugin plugin, NetworkRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.containerKey = new NamespacedKey(plugin, "network");
        this.upgradeKey = new NamespacedKey(plugin, "upgrade");
    }

    public void load() {
        networks.clear();
        networks.putAll(repository.loadNetworks());
        rebuildContainerMarkers();
    }

    public void save() {
        for (StorageNetwork network : networks.values()) {
            Inventory open = network.getInventory();

            if (open == null) {
                continue;
            }

            List<Inventory> containers = network.getOpenContainers();

            if (containers != null && !containers.isEmpty()) {
                saveCurrentPage(network, open);
                flush(network.getBuffer(), containers);
            }
        }

        repository.saveNetworks(networks);
    }

    public Map<String, StorageNetwork> getNetworks() {
        return Map.copyOf(networks);
    }

    public StorageNetwork getNetwork(String name) {
        if (name == null) {
            return null;
        }

        return networks.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean createNetwork(String name) {
        if (!isValidName(name) || getNetwork(name) != null) {
            return false;
        }

        networks.put(name.toLowerCase(Locale.ROOT), new StorageNetwork(name));
        save();
        return true;
    }

    public boolean deleteNetwork(String name) {
        StorageNetwork network = getNetwork(name);

        if (network == null || network.hasViewers()) {
            return false;
        }

        for (String key : network.getLinkedContainers()) {
            Block block = blockFromKey(key);

            if (block != null) {
                removeMarker(block);
            }
        }

        networks.remove(network.getName().toLowerCase(Locale.ROOT));
        pendingLinks.values().removeIf(value ->
                value.equalsIgnoreCase(network.getName()));

        save();
        return true;
    }

    public boolean startLink(Player player, String networkName) {
        StorageNetwork network = getNetwork(networkName);

        if (network == null) {
            return false;
        }

        pendingUnlinks.remove(player.getUniqueId());
        pendingLinks.put(player.getUniqueId(), network.getName());
        return true;
    }

    public void startUnlink(Player player) {
        pendingLinks.remove(player.getUniqueId());
        pendingUnlinks.put(player.getUniqueId(), true);
    }

    public boolean hasPendingAction(Player player) {
        UUID id = player.getUniqueId();
        return pendingLinks.containsKey(id) || pendingUnlinks.containsKey(id);
    }

    public String getPendingLinkNetwork(Player player) {
        return pendingLinks.get(player.getUniqueId());
    }

    public boolean isAtContainerLimit(String name) {
        StorageNetwork network = getNetwork(name);
        return network != null
                && network.getLinkedContainers().size() >= network.getMaxContainers();
    }

    public int getMaxContainers(String name) {
        StorageNetwork network = getNetwork(name);
        return network == null ? 0 : network.getMaxContainers();
    }

    public boolean linkContainer(Player player, Block block) {
        String networkName = pendingLinks.remove(player.getUniqueId());

        if (networkName == null || !isSupportedContainer(block)) {
            return false;
        }

        StorageNetwork network = getNetwork(networkName);

        if (network == null) {
            return false;
        }

        String key = StorageNetwork.locationKey(block.getLocation());

        if (!network.getLinkedContainers().contains(key)
                && network.getLinkedContainers().size() >= network.getMaxContainers()) {
            return false;
        }

        if (!(block.getState() instanceof TileState state)) {
            return false;
        }

        String existing = state.getPersistentDataContainer()
                .get(containerKey, PersistentDataType.STRING);

        if (existing != null && !existing.equalsIgnoreCase(network.getName())) {
            return false;
        }

        state.getPersistentDataContainer().set(
                containerKey,
                PersistentDataType.STRING,
                network.getName()
        );

        if (!state.update()) {
            return false;
        }

        network.addContainer(block.getLocation());
        save();
        return true;
    }

    public boolean unlinkContainer(Player player, Block block) {
        if (pendingUnlinks.remove(player.getUniqueId()) == null
                || !isSupportedContainer(block)) {
            return false;
        }

        String networkName = getMarkedNetwork(block);

        if (networkName == null) {
            return false;
        }

        StorageNetwork network = getNetwork(networkName);

        if (network != null) {
            network.removeContainer(block.getLocation());
        }

        removeMarker(block);
        save();
        return true;
    }

    public String getMarkedNetwork(Block block) {
        if (!(block.getState() instanceof TileState state)) {
            return null;
        }

        return state.getPersistentDataContainer()
                .get(containerKey, PersistentDataType.STRING);
    }

    public void openNetwork(Player player, String name) {
        StorageNetwork network = getNetwork(name);

        if (network == null) {
            player.sendMessage("§cThat storage network does not exist.");
            return;
        }

        Inventory inventory = network.getInventory();

        if (inventory == null) {
            List<Inventory> containers = resolveInventories(network);

            if (containers.isEmpty()) {
                player.sendMessage("§cThis network has no linked containers to open.");
                return;
            }

            network.setOpenContainers(containers);
            network.setBuffer(loadBuffer(containers));
            network.setCurrentPage(0);

            NetworkHolder holder = new NetworkHolder(network.getName());
            inventory = Bukkit.createInventory(
                    holder,
                    GUI_SIZE,
                    Component.text("VaultMesh: " + network.getName())
            );

            holder.setInventory(inventory);
            network.setInventory(inventory);
            renderPage(network, inventory);
        }

        player.openInventory(inventory);
    }

    public void handleInventoryClose(Inventory inventory, NetworkHolder holder) {
        StorageNetwork network = getNetwork(holder.getNetworkName());

        if (network == null) {
            return;
        }

        // Run after Bukkit has finished removing the closing viewer.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!inventory.getViewers().isEmpty()) {
                return;
            }

            List<Inventory> containers = network.getOpenContainers();

            if (containers != null && !containers.isEmpty()) {
                saveCurrentPage(network, inventory);
                flush(network.getBuffer(), containers);
            }

            clearSession(network);
            save();
        });
    }

    public void handlePageClick(Player player, Inventory inventory, NetworkHolder holder, int slot) {
        StorageNetwork network = getNetwork(holder.getNetworkName());

        if (network == null) {
            return;
        }

        if (slot == PREVIOUS_SLOT) {
            if (network.getCurrentPage() > 0) {
                navigateTo(network, inventory, network.getCurrentPage() - 1);
            }
            return;
        }

        if (slot == NEXT_SLOT) {
            int next = network.getCurrentPage() + 1;

            if (next < maxPages(network)) {
                navigateTo(network, inventory, next);
            }
            return;
        }

        if (slot == INFO_SLOT) {
            player.sendMessage("§6" + network.getName() + "§7 - page "
                    + (network.getCurrentPage() + 1) + "/" + maxPages(network)
                    + "§7, " + network.getLinkedContainers().size()
                    + "/" + network.getMaxContainers() + " containers");
        }
    }

    public void insertIntoNetwork(Inventory inventory, NetworkHolder holder, ItemStack stack) {
        if (stack == null || stack.getAmount() <= 0) {
            return;
        }

        StorageNetwork network = getNetwork(holder.getNetworkName());

        if (network == null) {
            return;
        }

        int editable = editableSlots(network);

        for (int i = 0; i < editable && stack.getAmount() > 0; i++) {
            ItemStack existing = inventory.getItem(i);

            if (existing == null || !existing.isSimilar(stack)) {
                continue;
            }

            int space = existing.getMaxStackSize() - existing.getAmount();

            if (space <= 0) {
                continue;
            }

            int move = Math.min(space, stack.getAmount());
            ItemStack updated = existing.clone();
            updated.setAmount(existing.getAmount() + move);
            inventory.setItem(i, updated);
            stack.setAmount(stack.getAmount() - move);
        }

        for (int i = 0; i < editable && stack.getAmount() > 0; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, stack.clone());
                stack.setAmount(0);
            }
        }
    }

    public boolean isStorageSlotLocked(NetworkHolder holder, int slot) {
        StorageNetwork network = getNetwork(holder.getNetworkName());

        if (network == null) {
            return true;
        }

        return slot < 0 || slot >= editableSlots(network);
    }

    private int editableSlots(StorageNetwork network) {
        ItemStack[] buffer = network.getBuffer();

        if (buffer == null) {
            return 0;
        }

        int offset = network.getCurrentPage() * PAGE_SIZE;

        return Math.max(0, Math.min(PAGE_SIZE, buffer.length - offset));
    }

    public boolean upgradeNetwork(Player player, String name) {
        StorageNetwork network = getNetwork(name);

        if (network == null) {
            player.sendMessage("§cThat storage network does not exist.");
            return false;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();

        if (!isUpgradeItem(hand)) {
            player.sendMessage("§cHold a VaultMesh Upgrade item to upgrade this network.");
            return true;
        }

        ItemStack remaining = hand.clone();
        remaining.setAmount(remaining.getAmount() - 1);
        player.getInventory().setItemInMainHand(
                remaining.getAmount() <= 0 ? null : remaining);

        network.setMaxContainers(network.getMaxContainers() + 1);

        Inventory open = network.getInventory();

        if (open != null) {
            saveCurrentPage(network, open);
            renderPage(network, open);
        }

        save();
        player.sendMessage("§aUpgraded §e" + network.getName() + "§a - it can now hold "
                + network.getMaxContainers() + " container(s).");
        return true;
    }

    public boolean isUpgradeItem(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();

        return meta != null && "true".equals(meta.getPersistentDataContainer()
                .get(upgradeKey, PersistentDataType.STRING));
    }

    public ItemStack createUpgradeItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("VaultMesh Upgrade")
                    .color(NamedTextColor.AQUA));
            meta.lore(List.of(
                    Component.text("Hold this and run")
                            .color(NamedTextColor.GRAY),
                    Component.text("/vm upgrade <network>")
                            .color(NamedTextColor.YELLOW),
                    Component.text("to add another container slot.")
                            .color(NamedTextColor.GRAY)
            ));
            meta.getPersistentDataContainer()
                    .set(upgradeKey, PersistentDataType.STRING, "true");
            item.setItemMeta(meta);
        }

        return item;
    }

    public void registerRecipes() {
        NamespacedKey key = new NamespacedKey(plugin, "upgrade");

        if (Bukkit.getRecipe(key) != null) {
            Bukkit.removeRecipe(key);
        }

        ShapedRecipe recipe = new ShapedRecipe(key, createUpgradeItem());
        recipe.shape("DED", "ENE", "DED");
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('E', Material.ENDER_PEARL);
        recipe.setIngredient('N', Material.NETHER_STAR);

        Bukkit.addRecipe(recipe);
    }

    public void cancelPendingAction(Player player) {
        pendingLinks.remove(player.getUniqueId());
        pendingUnlinks.remove(player.getUniqueId());
    }

    public static boolean isControlSlot(int slot) {
        return slot >= CONTROL_ROW_START;
    }

    private void navigateTo(StorageNetwork network, Inventory inventory, int page) {
        saveCurrentPage(network, inventory);
        network.setCurrentPage(page);
        renderPage(network, inventory);
    }

    private void saveCurrentPage(StorageNetwork network, Inventory inventory) {
        ItemStack[] buffer = network.getBuffer();

        if (buffer == null) {
            return;
        }

        int offset = network.getCurrentPage() * PAGE_SIZE;

        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = offset + i;

            if (index >= buffer.length) {
                break;
            }

            buffer[index] = inventory.getItem(i);
        }
    }

    private void renderPage(StorageNetwork network, Inventory inventory) {
        ItemStack[] buffer = network.getBuffer();

        if (buffer == null) {
            return;
        }

        int page = network.getCurrentPage();
        int offset = page * PAGE_SIZE;
        int maxPages = maxPages(network);

        ItemStack locked = titled(Material.BLACK_STAINED_GLASS_PANE,
                Component.text("No storage here").color(NamedTextColor.DARK_GRAY),
                null);

        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = offset + i;
            inventory.setItem(i, index < buffer.length ? buffer[index] : locked.clone());
        }

        ItemStack filler = titled(Material.GRAY_STAINED_GLASS_PANE,
                Component.text(" "), null);

        for (int slot = CONTROL_ROW_START; slot < GUI_SIZE; slot++) {
            inventory.setItem(slot, filler.clone());
        }

        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, titled(Material.ARROW,
                    Component.text("Previous page").color(NamedTextColor.YELLOW),
                    null));
        }

        if (page + 1 < maxPages) {
            inventory.setItem(NEXT_SLOT, titled(Material.ARROW,
                    Component.text("Next page").color(NamedTextColor.YELLOW),
                    null));
        }

        inventory.setItem(INFO_SLOT, titled(Material.BOOK,
                Component.text(network.getName()).color(NamedTextColor.GOLD),
                List.of(
                        Component.text("Page " + (page + 1) + " of " + maxPages)
                                .color(NamedTextColor.GRAY),
                        Component.text(network.getLinkedContainers().size()
                                        + " of " + network.getMaxContainers()
                                        + " container slots used")
                                .color(NamedTextColor.GRAY)
                )));
    }

    private ItemStack titled(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(name);

            if (lore != null) {
                meta.lore(lore);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private List<Inventory> resolveInventories(StorageNetwork network) {
        List<Inventory> inventories = new ArrayList<>();

        for (String key : network.getLinkedContainers()) {
            Block block = blockFromKey(key);

            if (block == null) {
                continue;
            }

            BlockState state = block.getState();

            if (state instanceof Chest chest) {
                inventories.add(chest.getBlockInventory());
            } else if (state instanceof Barrel barrel) {
                inventories.add(barrel.getInventory());
            }
        }

        return inventories;
    }

    private ItemStack[] loadBuffer(List<Inventory> containers) {
        int total = totalSlots(containers);
        ItemStack[] buffer = new ItemStack[total];
        int offset = 0;

        for (Inventory container : containers) {
            ItemStack[] contents = container.getContents();
            int size = container.getSize();

            for (int i = 0; i < size && offset + i < buffer.length; i++) {
                ItemStack stack = i < contents.length ? contents[i] : null;
                buffer[offset + i] = stack == null ? null : stack.clone();
            }

            offset += size;
        }

        return buffer;
    }

    private void flush(ItemStack[] buffer, List<Inventory> containers) {
        if (buffer == null) {
            return;
        }

        int offset = 0;

        for (Inventory container : containers) {
            int size = container.getSize();

            if (offset + size > buffer.length) {
                break;
            }

            ItemStack[] slice = new ItemStack[size];
            System.arraycopy(buffer, offset, slice, 0, size);

            try {
                container.setContents(slice);
            } catch (RuntimeException ignored) {
                // The container may have been removed while the network was open.
            }

            offset += size;
        }
    }

    private int maxPages(StorageNetwork network) {
        List<Inventory> containers = network.getOpenContainers();

        if (containers == null) {
            containers = resolveInventories(network);
        }

        return Math.max(1, (totalSlots(containers) + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private int totalSlots(List<Inventory> containers) {
        int total = 0;

        for (Inventory container : containers) {
            total += container.getSize();
        }

        return total;
    }

    private void clearSession(StorageNetwork network) {
        network.setBuffer(null);
        network.setOpenContainers(null);
        network.setCurrentPage(0);
        network.setInventory(null);
    }

    private void rebuildContainerMarkers() {
        for (StorageNetwork network : networks.values()) {
            for (String key : network.getLinkedContainers()) {
                Block block = blockFromKey(key);

                if (block == null || !(block.getState() instanceof TileState state)) {
                    continue;
                }

                state.getPersistentDataContainer().set(
                        containerKey,
                        PersistentDataType.STRING,
                        network.getName()
                );
                state.update();
            }
        }
    }

    private void removeMarker(Block block) {
        if (!(block.getState() instanceof TileState state)) {
            return;
        }

        state.getPersistentDataContainer().remove(containerKey);
        state.update();
    }

    private Block blockFromKey(String key) {
        String[] parts = key.split(",", 4);

        if (parts.length != 4) {
            return null;
        }

        var world = Bukkit.getWorld(parts[0]);

        if (world == null) {
            return null;
        }

        try {
            Location location = new Location(
                    world,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])
            );

            return location.getBlock();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * Returns the block material name for a linked container key, or {@code null}
     * if the world/coords are invalid or the chunk is not loaded. Must be called
     * from the primary server thread.
     */
    public String containerTypeName(String key) {
        Block block = blockFromKey(key);

        if (block == null) {
            return null;
        }

        if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
            return null;
        }

        return block.getType().name();
    }

    private boolean isSupportedContainer(Block block) {
        return block.getType().name().equals("CHEST")
                || block.getType().name().equals("TRAPPED_CHEST")
                || block.getType().name().equals("BARREL");
    }

    private boolean isValidName(String name) {
        return name != null
                && name.matches("[a-zA-Z0-9_-]{1,32}");
    }
}
