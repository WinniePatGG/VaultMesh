package de.winniepat.vaultMesh.listener;

import de.winniepat.vaultMesh.StorageManager;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
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

        if (!manager.hasPendingAction(player)) {
            return;
        }

        Block block = event.getClickedBlock();

        if (block == null || !isSupportedContainer(block)) {
            return;
        }

        event.setCancelled(true);

        BlockState state = block.getState();

        if (!(state instanceof TileState)) {
            player.sendMessage("§cThis container cannot be linked.");
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
    }

    private boolean isSupportedContainer(Block block) {
        return switch (block.getType()) {
            case CHEST, TRAPPED_CHEST, BARREL -> true;
            default -> false;
        };
    }
}
