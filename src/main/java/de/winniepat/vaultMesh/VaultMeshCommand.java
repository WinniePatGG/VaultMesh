package de.winniepat.vaultMesh;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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
                            + " containers, "
                            + (network.isPublic() ? "public" : "private") + ")");
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
            if (!requireCreate(sender)) {
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage("§cUsage: /vm create <name>");
                return true;
            }

            Player creator = sender instanceof Player player ? player : null;

            if (!manager.createNetwork(args[1], creator)) {
                sender.sendMessage("§cThat name is invalid or already exists.");
                return true;
            }

            sender.sendMessage("§aCreated storage network §e" + args[1]
                    + "§a. It is private to you until you share it.");
            return true;
        }

        if (subcommand.equals("delete")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /vm delete <name>");
                return true;
            }

            if (!allowManage(sender, args[1])) {
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
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by a player.");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage("§cUsage: /vm link <name>");
                return true;
            }

            if (!allowManage(player, args[1])) {
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
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by a player.");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage("§cUsage: /vm upgrade <name>");
                return true;
            }

            if (!allowManage(player, args[1])) {
                return true;
            }

            manager.upgradeNetwork(player, args[1]);
            return true;
        }

        if (subcommand.equals("members")) {
            handleMembers(sender, args);
            return true;
        }

        if (subcommand.equals("access")) {
            handleAccess(sender, args);
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

    private boolean requireCreate(CommandSender sender) {
        if (sender.hasPermission("vaultmesh.manage")
                || sender.hasPermission("vaultmesh.create")) {
            return true;
        }

        sender.sendMessage("§cYou don't have permission to create storage networks.");
        return false;
    }

    private boolean allowManage(CommandSender sender, String networkName) {
        if (!(sender instanceof Player player)) {
            return requireManage(sender);
        }

        if (player.hasPermission("vaultmesh.manage")) {
            return true;
        }

        StorageNetwork network = manager.getNetwork(networkName);

        if (network == null) {
            return true;
        }

        if (manager.canManage(player, network)) {
            return true;
        }

        player.sendMessage("§cYou don't own that storage network.");
        return false;
    }

    private void handleMembers(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /vm members <name> <list|add|remove> [player]");
            return;
        }

        String networkName = args[1];
        StorageNetwork network = manager.getNetwork(networkName);

        if (network == null) {
            sender.sendMessage("§cThat storage network does not exist.");
            return;
        }

        if (!allowManage(sender, networkName)) {
            return;
        }

        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "list";

        if (action.equals("list")) {
            sender.sendMessage("§6" + network.getName() + " members:");
            sender.sendMessage("§7Owner: §e" + displayName(network.getOwner(),
                    network.getOwnerName()));

            if (network.getMembers().isEmpty()) {
                sender.sendMessage("§7No members added yet.");
            } else {
                for (UUID member : network.getMembers()) {
                    sender.sendMessage("§e- " + displayName(member, null));
                }
            }

            sender.sendMessage("§7Access: §e"
                    + (network.isPublic() ? "public" : "private"));
            return;
        }

        if (args.length < 4) {
            sender.sendMessage("§cUsage: /vm members " + networkName + " "
                    + action + " <player>");
            return;
        }

        OfflinePlayer target = resolvePlayer(args[3]);

        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage("§cThat player has never joined this server.");
            return;
        }

        if (action.equals("add")) {
            if (network.getOwner() != null
                    && network.getOwner().equals(target.getUniqueId())) {
                sender.sendMessage("§cThat player already owns this network.");
                return;
            }

            if (manager.addMember(networkName, target.getUniqueId())) {
                sender.sendMessage("§aAdded §e" + displayName(target.getUniqueId(), null)
                        + "§a to §e" + network.getName() + "§a.");
            } else {
                sender.sendMessage("§cThat player is already a member.");
            }

            return;
        }

        if (action.equals("remove")) {
            if (manager.removeMember(networkName, target.getUniqueId())) {
                sender.sendMessage("§aRemoved §e" + displayName(target.getUniqueId(), null)
                        + "§a from §e" + network.getName() + "§a.");
            } else {
                sender.sendMessage("§cThat player is not a member of this network.");
            }

            return;
        }

        sender.sendMessage("§cUsage: /vm members <name> <list|add|remove> [player]");
    }

    private void handleAccess(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /vm access <name> <public|private>");
            return;
        }

        String networkName = args[1];
        StorageNetwork network = manager.getNetwork(networkName);

        if (network == null) {
            sender.sendMessage("§cThat storage network does not exist.");
            return;
        }

        if (!allowManage(sender, networkName)) {
            return;
        }

        String mode = args[2].toLowerCase(Locale.ROOT);

        if (!mode.equals("public") && !mode.equals("private")) {
            sender.sendMessage("§cUsage: /vm access <name> <public|private>");
            return;
        }

        manager.setPublic(networkName, mode.equals("public"));
        sender.sendMessage("§a" + network.getName() + " is now §e" + mode + "§a.");
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);

        if (online != null) {
            return online;
        }

        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private String displayName(UUID id, String fallback) {
        if (id == null) {
            return fallback == null ? "none" : fallback;
        }

        String name = Bukkit.getOfflinePlayer(id).getName();

        if (name != null) {
            return name;
        }

        return fallback != null ? fallback : id.toString();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6VaultMesh commands:");
        sender.sendMessage("§e/vm create <name> §7- Create a network");
        sender.sendMessage("§e/vm link <name> §7- Link a container");
        sender.sendMessage("§e/vm unlink §7- Unlink a container");
        sender.sendMessage("§e/vm open <name> §7- Open a network");
        sender.sendMessage("§e/vm upgrade <name> §7- Add a container slot");
        sender.sendMessage("§e/vm giveupgrade [amount] §7- Get upgrade items");
        sender.sendMessage("§e/vm members <name> <list|add|remove> [player] §7- Share a network");
        sender.sendMessage("§e/vm access <name> <public|private> §7- Set network access");
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
                    "members", "access", "list", "delete", "cancel"
            ));
        } else if (args.length == 2) {
            String subcommand = args[0].toLowerCase(Locale.ROOT);

            if (List.of("link", "open", "delete", "upgrade", "members", "access")
                    .contains(subcommand)) {
                suggestions.addAll(manager.getNetworks().values().stream()
                        .map(StorageNetwork::getName)
                        .toList());
            }
        } else if (args.length == 3) {
            String subcommand = args[0].toLowerCase(Locale.ROOT);

            if (subcommand.equals("members")) {
                suggestions.addAll(List.of("list", "add", "remove"));
            } else if (subcommand.equals("access")) {
                suggestions.addAll(List.of("public", "private"));
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("members")) {
            String action = args[2].toLowerCase(Locale.ROOT);

            if (action.equals("add") || action.equals("remove")) {
                suggestions.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .toList());
            }
        }

        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);

        return suggestions.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}
