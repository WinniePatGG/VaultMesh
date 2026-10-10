
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
    private int unlockedPages = 1;
    private Inventory inventory;
    private List<Inventory> openContainers;
    private ItemStack[] buffer;
    private int currentPage;

    public StorageNetwork(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Set<String> getLinkedContainers() {
        return linkedContainers;
    }

    public int getUnlockedPages() {
        return unlockedPages;
    }

    public void setUnlockedPages(int unlockedPages) {
        this.unlockedPages = Math.max(1, unlockedPages);
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

    public ItemStack[] getBuffer() {
        return buffer;
    }

    public void setBuffer(ItemStack[] buffer) {
        this.buffer = buffer;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = Math.max(0, currentPage);
    }

    public boolean hasViewers() {
        return inventory != null && !inventory.getViewers().isEmpty();
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
