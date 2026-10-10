package de.winniepat.vaultMesh;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NetworkRepository {

    private final JavaPlugin plugin;

    public NetworkRepository(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, StorageNetwork> loadNetworks(int inventorySize) {
        Map<String, StorageNetwork> networks = new LinkedHashMap<>();

        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("networks");

        if (section == null) {
            return networks;
        }

        for (String name : section.getKeys(false)) {
            StorageNetwork network = new StorageNetwork(name, inventorySize);

            ConfigurationSection networkSection =
                    section.getConfigurationSection(name);

            if (networkSection == null) {
                continue;
            }

            for (String key : networkSection.getStringList("containers")) {
                network.getLinkedContainers().add(key);
            }

            Object contents = networkSection.get("contents");

            if (contents instanceof org.bukkit.inventory.ItemStack[] items) {
                network.setContents(items);
            } else if (contents instanceof java.util.List<?> items) {
                org.bukkit.inventory.ItemStack[] array =
                        new org.bukkit.inventory.ItemStack[inventorySize];

                for (int i = 0; i < Math.min(items.size(), inventorySize); i++) {
                    Object item = items.get(i);

                    if (item instanceof org.bukkit.inventory.ItemStack stack) {
                        array[i] = stack;
                    }
                }

                network.setContents(array);
            }

            networks.put(name.toLowerCase(java.util.Locale.ROOT), network);
        }

        return networks;
    }

    public void saveNetworks(Map<String, StorageNetwork> networks) {
        FileConfiguration config = plugin.getConfig();
        config.set("networks", null);

        for (StorageNetwork network : networks.values()) {
            String path = "networks." + network.getName();

            config.set(path + ".containers",
                    network.getLinkedContainers().stream().toList());

            config.set(path + ".contents",
                    network.getInventory() != null
                            ? network.getInventory().getContents()
                            : network.getContents());
        }

        plugin.saveConfig();
    }
}
