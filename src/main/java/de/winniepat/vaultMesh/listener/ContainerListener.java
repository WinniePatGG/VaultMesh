package de.winniepat.vaultMesh.listener;

import de.winniepat.vaultMesh.StorageManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class ContainerListener implements Listener {

    private final StorageManager manager;

    public ContainerListener(StorageManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onContainerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (block == null || !isSupportedContainer(block)) {
            return;
        }

        if (event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }

        if (manager.hasPendingAction(player)) {
            event.setCancelled(true);

            BlockState state = block.getState();

            if (!(state instanceof TileState)) {
                player.sendMessage("§cThis container cannot be linked.");
                manager.cancelPendingAction(player);
                return;
            }

            String pendingLink = manager.getPendingLinkNetwork(player);

            if (pendingLink != null && manager.isAtContainerLimit(pendingLink)) {
                player.sendMessage("§c" + pendingLink + " is full ("
                        + manager.getMaxContainers(pendingLink) + " container slots).");
                player.sendMessage("§7Use /vm upgrade " + pendingLink
                        + " to add another slot.");
                manager.cancelPendingAction(player);
                return;
            }

            if (manager.linkContainer(player, block)) {
                player.sendMessage("§aContainer linked successfully.");
                return;
            }

            if (manager.unlinkContainer(player, block)) {
                player.sendMessage("§aContainer unlinked successfully.");
                return;
            }

            player.sendMessage("§cCould not link or unlink this container.");
            player.sendMessage("§7It may already belong to another network.");
            return;
        }

        if (player.isSneaking()) {
            return;
        }

        String networkName = manager.getMarkedNetwork(block);

        if (networkName == null) {
            return;
        }

        event.setCancelled(true);
        manager.openNetwork(player, networkName);
    }

    private boolean isSupportedContainer(Block block) {
        Material type = block.getType();

        return type == Material.CHEST
                || type == Material.TRAPPED_CHEST
                || type == Material.BARREL;
    }
}
