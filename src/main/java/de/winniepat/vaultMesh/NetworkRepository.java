package de.winniepat.vaultMesh;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class NetworkRepository {

    private final JavaPlugin plugin;

    public NetworkRepository(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, StorageNetwork> loadNetworks() {
        Map<String, StorageNetwork> networks = new LinkedHashMap<>();

        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("networks");

        if (section == null) {
            return networks;
        }

        for (String name : section.getKeys(false)) {
            ConfigurationSection networkSection =
                    section.getConfigurationSection(name);

            if (networkSection == null) {
                continue;
            }

            StorageNetwork network = new StorageNetwork(name);

            for (String key : networkSection.getStringList("containers")) {
                network.getLinkedContainers().add(key);
            }

            network.setUnlockedPages(networkSection.getInt("pages", 1));
            networks.put(name.toLowerCase(Locale.ROOT), network);
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
            config.set(path + ".pages", network.getUnlockedPages());
        }

        plugin.saveConfig();
    }
}
