
package de.winniepat.vaultMesh;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class StorageManager {

    public static final int INVENTORY_SIZE = 54;

    private final JavaPlugin plugin;
    private final NetworkRepository repository;
    private final NamespacedKey containerKey;

    private final Map<String, StorageNetwork> networks = new LinkedHashMap<>();
    private final Map<UUID, String> pendingLinks = new HashMap<>();
    private final Map<UUID, Boolean> pendingUnlinks = new HashMap<>();

    public StorageManager(JavaPlugin plugin, NetworkRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.containerKey = new NamespacedKey(plugin, "network");
    }

    public void load() {
        networks.clear();
        networks.putAll(repository.loadNetworks(INVENTORY_SIZE));
        rebuildContainerMarkers();
    }

    public void save() {
        for (StorageNetwork network : networks.values()) {
            if (network.getInventory() != null) {
                network.setContents(network.getInventory().getContents());
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

        networks.put(name.toLowerCase(Locale.ROOT),
                new StorageNetwork(name, INVENTORY_SIZE));
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

    public boolean linkContainer(Player player, Block block) {
        String networkName = pendingLinks.remove(player.getUniqueId());

        if (networkName == null || !isSupportedContainer(block)) {
            return false;
        }

        StorageNetwork network = getNetwork(networkName);

        if (network == null) {
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
            NetworkHolder holder = new NetworkHolder(network.getName());
            inventory = Bukkit.createInventory(
                    holder,
                    INVENTORY_SIZE,
                    "VaultMesh: " + network.getName()
            );

            holder.setInventory(inventory);
            inventory.setContents(network.getContents());
            network.setInventory(inventory);
        }

        player.openInventory(inventory);
    }

    public void handleInventoryClose(Inventory inventory, NetworkHolder holder) {
        StorageNetwork network = getNetwork(holder.getNetworkName());

        if (network == null) {
            return;
        }

        network.setContents(inventory.getContents());

        // Run after Bukkit has finished removing the closing viewer.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (inventory.getViewers().isEmpty()) {
                network.setContents(inventory.getContents());
                network.setInventory(null);
            }

            save();
        });
    }

    public void cancelPendingAction(Player player) {
        pendingLinks.remove(player.getUniqueId());
        pendingUnlinks.remove(player.getUniqueId());
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
