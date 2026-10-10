package de.winniepat.vaultMesh.listener;

import de.winniepat.vaultMesh.NetworkHolder;
import de.winniepat.vaultMesh.StorageManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class StorageInventoryListener implements Listener {

    private final StorageManager manager;

    public StorageInventoryListener(StorageManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();

        if (!(holder instanceof NetworkHolder networkHolder)) {
            return;
        }

        manager.handleInventoryClose(inventory, networkHolder);
    }
}
