package me.zpleum.zmystic.quest.commands;

import me.zpleum.zmystic.quest.MysticQuest;
import me.zpleum.zmystic.quest.models.QuestNPC;
import me.zpleum.zmystic.quest.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class AdminCommands implements CommandExecutor, TabCompleter {

    private final MysticQuest plugin;

    public AdminCommands(MysticQuest plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Check permission
        if (!player.hasPermission("mysticquest.admin")) {
            MessageUtils.sendMessage(player, "&cYou don't have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            showHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "summon":
                handleSummonCommand(player, args);
                break;
            case "kill":
                handleKillCommand(player, args);
                break;
            case "killall":
                handleKillAllCommand(player, args);
                break;
            case "list":
                handleListCommand(player, args);
                break;
            case "resetplayer":
                handleResetPlayer(player, args);
                break;
            case "help":
            default:
                showHelp(player);
                break;
        }

        return true;
    }

    private void handleSummonCommand(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtils.sendMessage(player, "&cUsage: /mqadmin summon <type> [player]");
            return;
        }

        String npcType = args[1];
        Player targetPlayer = player;

        // If a target player is specified, use that instead
        if (args.length > 2) {
            targetPlayer = Bukkit.getPlayer(args[2]);
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                MessageUtils.sendMessage(player, "&cPlayer &e" + args[2] + " &cis not online.");
                return;
            }
        }

        // Spawn the NPC
        plugin.getNPCManager().spawnNPCForPlayer(targetPlayer, npcType);
        MessageUtils.sendMessage(player, "&aSpawned NPC of type &e" + npcType + " &afor player &e" + targetPlayer.getName());
    }

    private void handleKillCommand(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtils.sendMessage(player, "&cUsage: /mqadmin kill <npc_id>");
            return;
        }

        try {
            UUID npcId = UUID.fromString(args[1]);
            QuestNPC npc = plugin.getNPCManager().getNPC(npcId);

            if (npc == null) {
                MessageUtils.sendMessage(player, "&cNo NPC found with ID &e" + npcId);
                return;
            }

            // Remove the NPC
            plugin.getNPCManager().removeNPC(npcId);
            MessageUtils.sendMessage(player, "&aRemoved NPC with ID &e" + npcId);

        } catch (IllegalArgumentException e) {
            MessageUtils.sendMessage(player, "&cInvalid NPC ID format. Use the UUID from the list command.");
        }
    }

    private void handleKillAllCommand(Player player, String[] args) {
        // Check if we should only remove NPCs for a specific player
        Player targetPlayer = null;
        if (args.length > 1) {
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                MessageUtils.sendMessage(player, "&cPlayer &e" + args[1] + " &cis not online.");
                return;
            }
        }

        Collection<QuestNPC> npcs = new ArrayList<>(plugin.getNPCManager().getActiveNPCs());
        int count = 0;

        for (QuestNPC npc : npcs) {
            // If target player specified, only remove NPCs for that player
            if (targetPlayer != null && !npc.getTargetPlayer().equals(targetPlayer.getUniqueId())) {
                continue;
            }

            plugin.getNPCManager().removeNPC(npc.getUuid());
            count++;
        }

        if (targetPlayer != null) {
            MessageUtils.sendMessage(player, "&aRemoved &e" + count + " &aNPCs targeting player &e" + targetPlayer.getName() + "&a.");
        } else {
            MessageUtils.sendMessage(player, "&aRemoved &e" + count + " &aNPCs from the world.");
        }
    }

    private void handleListCommand(Player player, String[] args) {
        // หาเป้าหมาย (ถ้ามี)
        Player targetPlayer = null;
        if (args.length > 1) {
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                MessageUtils.sendMessage(player, "&cPlayer &e" + args[1] + " &cis not online.");
                return;
            }
        }

        Collection<QuestNPC> npcs = plugin.getNPCManager().getActiveNPCs();

        // ถ้ามี targetPlayer ให้กรองด้วย UUID ค้างไว้ในตัวแปรใหม่ (effectively final)
        if (targetPlayer != null) {
            final UUID filterUuid = targetPlayer.getUniqueId();
            npcs = npcs.stream()
                    .filter(npc -> npc.getTargetPlayer().equals(filterUuid))
                    .collect(Collectors.toList());
        }

        if (npcs.isEmpty()) {
            if (targetPlayer != null) {
                MessageUtils.sendMessage(player, "&eNo active NPCs found for player &6" + targetPlayer.getName() + "&e.");
            } else {
                MessageUtils.sendMessage(player, "&eNo active NPCs found.");
            }
            return;
        }

        MessageUtils.sendMessage(player, targetPlayer != null
                ? "&6Active NPCs for player &e" + targetPlayer.getName() + "&6:"
                : "&6Active NPCs:");

        for (QuestNPC npc : npcs) {
            Player npcTarget = Bukkit.getPlayer(npc.getTargetPlayer());
            String playerName = (npcTarget != null ? npcTarget.getName() : "Unknown");
            MessageUtils.sendMessage(player,
                    "&e- &6" + npc.getName() +
                            " &7(Type: &f" + npc.getNpcType() +
                            "&7, Target: &f" + playerName +
                            "&7, ID: &f" + npc.getUuid() + "&7)"
            );
        }
    }

    private void handleResetPlayer(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtils.sendMessage(player, "&cUsage: /mqadmin resetplayer <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            MessageUtils.sendMessage(player, "&cPlayer not found or offline.");
            return;
        }

        // Force reset player movement and effects
        resetPlayerState(target);
        MessageUtils.sendMessage(player, "&aReset movement state for player " + target.getName());
    }

    private void resetPlayerState(Player player) {
        // Remove all NPC-related effects
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, 1, false, false));

        // Reset movement speeds to default
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);

        // Turn off invulnerability
        player.setInvulnerable(false);

        // Check if the player has a pending NPC interaction and reset it
        if (plugin.getNPCManager().hasPlayerPendingResponse(player.getUniqueId())) {
            // Clear their pending responses
            plugin.getNPCManager().clearPlayerPendingResponses(player.getUniqueId());
        }

        // Let player know they've been reset
        MessageUtils.sendMessage(player, "&aYour player state has been reset by an admin.");
    }

    private void showHelp(Player player) {
        MessageUtils.sendMessage(player, "&6MysticQuest Admin Commands:");
        MessageUtils.sendMessage(player, "&e/mqadmin summon <type> [player] &7- Spawn an NPC of specified type");
        MessageUtils.sendMessage(player, "&e/mqadmin kill <npc_id> &7- Remove a specific NPC by ID");
        MessageUtils.sendMessage(player, "&e/mqadmin killall [player] &7- Remove all active NPCs, optionally only for a specific player");
        MessageUtils.sendMessage(player, "&e/mqadmin list [player] &7- List all active NPCs, optionally only for a specific player");
        MessageUtils.sendMessage(player, "&e/mqadmin resetplayer <player> &7- Reset a player's movement state");
        MessageUtils.sendMessage(player, "&e/mqadmin help &7- Show this help message");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument - subcommands
            List<String> subcommands = List.of("summon", "kill", "killall", "list", "resetplayer", "help");
            String input = args[0].toLowerCase();

            for (String subcmd : subcommands) {
                if (subcmd.startsWith(input)) {
                    completions.add(subcmd);
                }
            }
        } else if (args.length == 2) {
            // Second argument - depends on subcommand
            if (args[0].equalsIgnoreCase("summon")) {
                // For summon, suggest NPC types
                String input = args[1].toLowerCase();
                try {
                    // Get NPC types from config
                    plugin.getConfigManager().getConfig("npcs.yml")
                            .getKeys(false)
                            .stream()
                            .filter(key -> key.toLowerCase().startsWith(input))
                            .forEach(completions::add);
                } catch (Exception ignored) {
                    // If something goes wrong, don't suggest anything
                }
            } else if (args[0].equalsIgnoreCase("kill")) {
                // For kill, suggest NPC IDs
                String input = args[1].toLowerCase();
                plugin.getNPCManager().getActiveNPCs()
                        .stream()
                        .map(npc -> npc.getUuid().toString())
                        .filter(id -> id.toLowerCase().startsWith(input))
                        .forEach(completions::add);
            } else if (args[0].equalsIgnoreCase("killall") || args[0].equalsIgnoreCase("list")) {
                // For killall and list, suggest player names
                String input = args[1].toLowerCase();
                return Bukkit.getOnlinePlayers()
                        .stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("resetplayer")) {
                // For resetplayer, suggest player names
                String input = args[1].toLowerCase();
                return Bukkit.getOnlinePlayers()
                        .stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            // Third argument - for summon, suggest player names
            if (args[0].equalsIgnoreCase("summon")) {
                String input = args[2].toLowerCase();
                return Bukkit.getOnlinePlayers()
                        .stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }
}