package de.winniepat.vaultMesh;

import de.winniepat.vaultMesh.listener.ContainerListener;
import de.winniepat.vaultMesh.listener.StorageInventoryListener;
import de.winniepat.vaultMesh.web.WebServer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public final class VaultMesh extends JavaPlugin {

    private StorageManager storageManager;
    private WebServer webServer;

    @Override
    public void onEnable() {
        ensureWebConfig();

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

        startWebServer();

        getLogger().info("VaultMesh enabled.");
    }

    @Override
    public void onDisable() {
        if (webServer != null) {
            webServer.stop();
            webServer = null;
        }

        if (storageManager != null) {
            storageManager.save();
        }
    }

    private void ensureWebConfig() {
        FileConfiguration config = getConfig();
        boolean changed = false;

        changed |= setIfMissing(config, "web.enabled", false);
        changed |= setIfMissing(config, "web.host", "127.0.0.1");
        changed |= setIfMissing(config, "web.port", 8080);
        changed |= setIfMissing(config, "web.token", "");

        if (changed) {
            saveConfig();
        }
    }

    private boolean setIfMissing(FileConfiguration config, String path, Object value) {
        if (config.isSet(path)) {
            return false;
        }

        config.set(path, value);
        return true;
    }

    private void startWebServer() {
        FileConfiguration config = getConfig();

        if (!config.getBoolean("web.enabled", false)) {
            return;
        }

        String host = config.getString("web.host", "127.0.0.1");
        int port = config.getInt("web.port", 8080);
        String token = config.getString("web.token", "");

        webServer = new WebServer(this, storageManager, host, port, token);

        try {
            webServer.start();
        } catch (IOException exception) {
            getLogger().severe("Failed to start VaultMesh web server on "
                    + host + ":" + port + " - " + exception.getMessage());
            webServer = null;
        }
    }
}
