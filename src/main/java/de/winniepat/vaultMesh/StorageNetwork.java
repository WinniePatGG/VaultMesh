
package de.winniepat.vaultMesh;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class StorageNetwork {

    private final String name;
    private final Set<String> linkedContainers = new LinkedHashSet<>();
    private ItemStack[] contents;
    private Inventory inventory;
    private List<Inventory> openContainers;

    public StorageNetwork(String name, int inventorySize) {
        this.name = name;
        this.contents = new ItemStack[inventorySize];
    }

    public String getName() {
        return name;
    }

    public Set<String> getLinkedContainers() {
        return linkedContainers;
    }

    public ItemStack[] getContents() {
        return contents;
    }

    public void setContents(ItemStack[] contents) {
        this.contents = contents;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public List<Inventory> getOpenContainers() {
        return openContainers;
    }

    public void setOpenContainers(List<Inventory> openContainers) {
        this.openContainers = openContainers;
    }

    public boolean hasViewers() {
        return inventory != null && inventory.getViewers().size() > 0;
    }

    public void addContainer(Location location) {
        linkedContainers.add(locationKey(location));
    }

    public void removeContainer(Location location) {
        linkedContainers.remove(locationKey(location));
    }

    public static String locationKey(Location location) {
        return location.getWorld().getName()
                + "," + location.getBlockX()
                + "," + location.getBlockY()
                + "," + location.getBlockZ();
    }
}
