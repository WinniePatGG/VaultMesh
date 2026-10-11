
package de.winniepat.vaultMesh;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class StorageNetwork {

    private final String name;
    private final Set<String> linkedContainers = new LinkedHashSet<>();
    private final Set<UUID> members = new LinkedHashSet<>();
    private int maxContainers = 1;
    private UUID owner;
    private String ownerName;
    private boolean publicAccess = true;
    private long createdTime = System.currentTimeMillis();
    private long openCount;
    private long lastOpened;
    private long storedItems;
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

    public int getMaxContainers() {
        return maxContainers;
    }

    public void setMaxContainers(int maxContainers) {
        this.maxContainers = Math.max(1, maxContainers);
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwner(UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = ownerName;
    }

    public boolean isPublic() {
        return publicAccess;
    }

    public void setPublic(boolean publicAccess) {
        this.publicAccess = publicAccess;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public boolean isMember(UUID id) {
        return id != null && members.contains(id);
    }

    public void addMember(UUID id) {
        if (id != null) {
            members.add(id);
        }
    }

    public void removeMember(UUID id) {
        members.remove(id);
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }

    public long getOpenCount() {
        return openCount;
    }

    public void setOpenCount(long openCount) {
        this.openCount = Math.max(0, openCount);
    }

    public long getLastOpened() {
        return lastOpened;
    }

    public void setLastOpened(long lastOpened) {
        this.lastOpened = Math.max(0, lastOpened);
    }

    public void markOpened() {
        openCount++;
        lastOpened = System.currentTimeMillis();
    }

    public long getStoredItems() {
        return storedItems;
    }

    public void setStoredItems(long storedItems) {
        this.storedItems = Math.max(0, storedItems);
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
