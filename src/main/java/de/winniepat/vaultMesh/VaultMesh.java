package de.winniepat.vaultMesh;

import de.winniepat.vaultMesh.listener.ContainerListener;
import de.winniepat.vaultMesh.listener.StorageInventoryListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class VaultMesh extends JavaPlugin {

    private StorageManager storageManager;

    @Override
    public void onEnable() {
        NetworkRepository repository = new NetworkRepository(this);
        storageManager = new StorageManager(this, repository);
        storageManager.load();
        storageManager.registerRecipes();

        VaultMeshCommand command = new VaultMeshCommand(storageManager);

        if (getCommand("vaultmesh") == null) {
            getLogger().severe("Command 'vaultmesh' not found in plugin.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("vaultmesh").setExecutor(command);
        getCommand("vaultmesh").setTabCompleter(command);

        getServer().getPluginManager().registerEvents(new ContainerListener(storageManager), this);
        getServer().getPluginManager().registerEvents(new StorageInventoryListener(storageManager), this);

        getLogger().info("VaultMesh enabled.");
    }

    @Override
    public void onDisable() {
        if (storageManager != null) {
            storageManager.save();
        }
    }
}
