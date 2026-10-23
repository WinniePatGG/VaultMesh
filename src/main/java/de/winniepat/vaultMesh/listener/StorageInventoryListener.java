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
import org.bukkit.inventory.PlayerInventory;

public final class StorageInventoryListener implements Listener {

    private final StorageManager manager;

    public StorageInventoryListener(StorageManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();

        if (!(holder instanceof NetworkHolder)) {
            return;
        }

        manager.handleInventoryClose(inventory, (NetworkHolder) holder);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // NB: never call methods on event.getView(). InventoryView changed from an
        // abstract class to an interface in 1.21, so call sites compiled against the
        // old API throw IncompatibleClassChangeError on 1.21+/26.x servers. The
        // getInventory() contract (the top inventory of the open view) is identical
        // on every supported version from 1.16.3 to 26.3.
        Inventory top = event.getInventory();

        InventoryHolder topHolder = top.getHolder();

        if (!(topHolder instanceof NetworkHolder)) {
            return;
        }

        NetworkHolder holder = (NetworkHolder) topHolder;

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

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
                && event.getClickedInventory() instanceof PlayerInventory) {
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
        Inventory top = event.getInventory();
        InventoryHolder topHolder = top.getHolder();

        if (!(topHolder instanceof NetworkHolder)) {
            return;
        }

        NetworkHolder holder = (NetworkHolder) topHolder;

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
