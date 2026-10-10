package de.winniepat.vaultMesh;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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

            network.setMaxContainers(networkSection.getInt(
                    "max-containers",
                    network.getLinkedContainers().size()));

            String ownerId = networkSection.getString("owner");

            if (ownerId != null) {
                try {
                    network.setOwner(
                            UUID.fromString(ownerId),
                            networkSection.getString("owner-name"));
                } catch (IllegalArgumentException ignored) {
                    // Malformed owner uuid - treat the network as unowned.
                }
            }

            network.setPublic(networkSection.getBoolean("public", ownerId == null));

            for (String memberId : networkSection.getStringList("members")) {
                try {
                    network.addMember(UUID.fromString(memberId));
                } catch (IllegalArgumentException ignored) {
                    // Skip malformed member uuid.
                }
            }

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
            config.set(path + ".max-containers", network.getMaxContainers());

            if (network.getOwner() != null) {
                config.set(path + ".owner", network.getOwner().toString());
                config.set(path + ".owner-name", network.getOwnerName());
            }

            config.set(path + ".public", network.isPublic());
            config.set(path + ".members",
                    network.getMembers().stream().map(UUID::toString).toList());
        }

        plugin.saveConfig();
    }
}
