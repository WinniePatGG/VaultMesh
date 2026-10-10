package de.winniepat.vaultMesh.listener;

import de.winniepat.vaultMesh.NetworkHolder;
import de.winniepat.vaultMesh.StorageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();

        if (!(top.getHolder() instanceof NetworkHolder holder)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int raw = event.getRawSlot();

        if (raw >= 0 && raw < top.getSize()) {
            if (StorageManager.isControlSlot(raw)) {
                event.setCancelled(true);
                manager.handlePageClick(player, top, holder, raw);
                return;
            }

            if (manager.isStorageSlotLocked(holder, raw)) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getBottomInventory())) {
            event.setCancelled(true);

            ItemStack moving = event.getCurrentItem();

            if (moving != null) {
                ItemStack toMove = moving.clone();
                manager.insertIntoNetwork(top, holder, toMove);
                event.setCurrentItem(toMove.getAmount() <= 0 ? null : toMove);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();

        if (!(top.getHolder() instanceof NetworkHolder holder)) {
            return;
        }

        for (int slot : event.getRawSlots()) {
            if (slot < 0 || slot >= top.getSize()) {
                continue;
            }

            if (StorageManager.isControlSlot(slot)
                    || manager.isStorageSlotLocked(holder, slot)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
