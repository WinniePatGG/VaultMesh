package de.winniepat.vaultMesh;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class NetworkHolder implements InventoryHolder {

    private final String networkName;
    private Inventory inventory;

    public NetworkHolder(String networkName) {
        this.networkName = networkName;
    }

    public String getNetworkName() {
        return networkName;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
