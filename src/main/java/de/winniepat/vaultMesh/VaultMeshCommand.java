package de.winniepat.vaultMesh;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VaultMeshCommand implements CommandExecutor, TabCompleter {

    private final StorageManager manager;

    public VaultMeshCommand(StorageManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);

        if (subcommand.equals("list")) {
            if (!sender.hasPermission("vaultmesh.use")) {
                sender.sendMessage("§cYou don't have permission to use VaultMesh.");
                return true;
            }

            sender.sendMessage("§6VaultMesh networks:");

            if (manager.getNetworks().isEmpty()) {
                sender.sendMessage("§7No networks have been created.");
            } else {
                for (StorageNetwork network : manager.getNetworks().values()) {
                    sender.sendMessage("§e- " + network.getName()
                            + " §7(" + network.getLinkedContainers().size()
                            + " containers)");
                }
            }

            return true;
        }

        if (subcommand.equals("cancel")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by a player.");
                return true;
            }

            manager.cancelPendingAction(player);
            player.sendMessage("§7Pending VaultMesh action cancelled.");
            return true;
        }

        if (subcommand.equals("create")) {
            if (!requireManage(sender)) {
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage("§cUsage: /vm create <name>");
                return true;
            }

            if (!manager.createNetwork(args[1])) {
                sender.sendMessage("§cThat name is invalid or already exists.");
                return true;
            }

            sender.sendMessage("§aCreated storage network §e" + args[1] + "§a.");
            return true;
        }

        if (subcommand.equals("delete")) {
            if (!requireManage(sender)) {
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage("§cUsage: /vm delete <name>");
                return true;
            }

            if (!manager.deleteNetwork(args[1])) {
                sender.sendMessage("§cCould not delete that network. Check that it exists and has no open viewers.");
                return true;
            }

            sender.sendMessage("§aDeleted storage network §e" + args[1] + "§a.");
            return true;
        }

        if (subcommand.equals("link")) {
            if (!requireManage(sender)) {
                return true;
            }

            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by a player.");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage("§cUsage: /vm link <name>");
                return true;
            }

            if (!manager.startLink(player, args[1])) {
                sender.sendMessage("§cThat storage network does not exist.");
                return true;
            }

            player.sendMessage("§aRight-click a chest, trapped chest, or barrel to link it.");
            player.sendMessage("§7Use /vm cancel to cancel.");
            return true;
        }

        if (subcommand.equals("unlink")) {
            if (!requireManage(sender)) {
                return true;
            }

            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by a player.");
                return true;
            }

            manager.startUnlink(player);
            player.sendMessage("§eRight-click a linked container to unlink it.");
            player.sendMessage("§7Use /vm cancel to cancel.");
            return true;
        }

        if (subcommand.equals("open")) {
            if (!sender.hasPermission("vaultmesh.use")) {
                sender.sendMessage("§cYou don't have permission to use VaultMesh.");
                return true;
            }

            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by a player.");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage("§cUsage: /vm open <name>");
                return true;
            }

            manager.openNetwork(player, args[1]);
            return true;
        }

        if (subcommand.equals("upgrade")) {
            if (!requireManage(sender)) {
                return true;
            }

            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by a player.");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage("§cUsage: /vm upgrade <name>");
                return true;
            }

            manager.upgradeNetwork(player, args[1]);
            return true;
        }

        if (subcommand.equals("giveupgrade")) {
            if (!requireManage(sender)) {
                return true;
            }

            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by a player.");
                return true;
            }

            int amount = 1;

            if (args.length >= 2) {
                try {
                    amount = Math.max(1, Math.min(64, Integer.parseInt(args[1])));
                } catch (NumberFormatException exception) {
                    player.sendMessage("§cInvalid amount.");
                    return true;
                }
            }

            ItemStack item = manager.createUpgradeItem();
            item.setAmount(amount);

            for (ItemStack leftover : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }

            player.sendMessage("§aGave you " + amount + " VaultMesh Upgrade(s).");
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private boolean requireManage(CommandSender sender) {
        if (sender.hasPermission("vaultmesh.manage")) {
            return true;
        }

        sender.sendMessage("§cYou don't have permission to manage VaultMesh.");
        return false;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6VaultMesh commands:");
        sender.sendMessage("§e/vm create <name> §7- Create a network");
        sender.sendMessage("§e/vm link <name> §7- Link a container");
        sender.sendMessage("§e/vm unlink §7- Unlink a container");
        sender.sendMessage("§e/vm open <name> §7- Open a network");
        sender.sendMessage("§e/vm upgrade <name> §7- Add a container slot");
        sender.sendMessage("§e/vm giveupgrade [amount] §7- Get upgrade items");
        sender.sendMessage("§e/vm list §7- List networks");
        sender.sendMessage("§e/vm delete <name> §7- Delete a network");
        sender.sendMessage("§e/vm cancel §7- Cancel a pending action");
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions.addAll(List.of(
                    "create", "link", "unlink", "open", "upgrade", "giveupgrade",
                    "list", "delete", "cancel"
            ));
        } else if (args.length == 2) {
            String subcommand = args[0].toLowerCase(Locale.ROOT);

            if (List.of("link", "open", "delete", "upgrade").contains(subcommand)) {
                suggestions.addAll(manager.getNetworks().values().stream()
                        .map(StorageNetwork::getName)
                        .toList());
            }
        }

        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);

        return suggestions.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}
