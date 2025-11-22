package me.zpleum.zmystic.quest.commands;

import me.zpleum.zmystic.quest.MysticQuest;
import me.zpleum.zmystic.quest.models.Quest;
import me.zpleum.zmystic.quest.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MysticQuestCommand implements CommandExecutor, TabCompleter {
    
    private final MysticQuest plugin;
    
    public MysticQuestCommand(MysticQuest plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("mysticquest.admin")) {
                    MessageUtils.sendMessage((Player) sender, "&cYou don't have permission to use this command.");
                    return true;
                }
                
                plugin.getConfigManager().reloadAllConfigs();
                MessageUtils.sendMessage((Player) sender, "&aConfigurations reloaded successfully.");
                return true;
                
            case "info":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }
                
                Player player = (Player) sender;
                sendPlayerInfo(player);
                return true;
                
            case "spawnnpc":
                if (!(sender instanceof Player) || !sender.hasPermission("mysticquest.admin")) {
                    MessageUtils.sendMessage((Player) sender, "&cYou don't have permission to use this command.");
                    return true;
                }
                
                if (args.length < 2) {
                    MessageUtils.sendMessage((Player) sender, "&cUsage: /mysticquest spawnnpc <type>");
                    return true;
                }
                
                String npcType = args[1];
                Player targetPlayer = (Player) sender;
                
                plugin.getNPCManager().spawnNPCForPlayer(targetPlayer, npcType);
                MessageUtils.sendMessage(targetPlayer, "&aSpawned an NPC of type " + npcType);
                return true;
                
            case "resetxp":
                if (!sender.hasPermission("mysticquest.admin")) {
                    MessageUtils.sendMessage((Player) sender, "&cYou don't have permission to use this command.");
                    return true;
                }
                
                if (args.length < 2) {
                    MessageUtils.sendMessage((Player) sender, "&cUsage: /mysticquest resetxp <player>");
                    return true;
                }
                
                Player targetPlayerXP = Bukkit.getPlayer(args[1]);
                if (targetPlayerXP == null) {
                    MessageUtils.sendMessage((Player) sender, "&cPlayer not found.");
                    return true;
                }
                
                plugin.getQuestManager().addXP(targetPlayerXP.getUniqueId(), -plugin.getQuestManager().getPlayerXP(targetPlayerXP.getUniqueId()));
                MessageUtils.sendMessage((Player) sender, "&aReset XP for player " + targetPlayerXP.getName());
                return true;
                
            default:
                sendHelpMessage(sender);
                return true;
        }
    }
    
    private void sendHelpMessage(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            
            MessageUtils.sendMessage(player, "&6MysticQuest Commands:");
            MessageUtils.sendMessage(player, "&e/mysticquest info &7- View your quest information");
            
            if (player.hasPermission("mysticquest.admin")) {
                MessageUtils.sendMessage(player, "&e/mysticquest reload &7- Reload configuration files");
                MessageUtils.sendMessage(player, "&e/mysticquest spawnnpc <type> &7- Spawn a quest NPC");
                MessageUtils.sendMessage(player, "&e/mysticquest resetxp <player> &7- Reset a player's Mystic XP");
            }
        } else {
            sender.sendMessage("MysticQuest Commands:");
            sender.sendMessage("/mysticquest reload - Reload configuration files");
            sender.sendMessage("/mysticquest resetxp <player> - Reset a player's Mystic XP");
        }
    }
    
    private void sendPlayerInfo(Player player) {
        // Get player quests
        Set<Quest> quests = plugin.getQuestManager().getPlayerQuests(player.getUniqueId());
        int playerXP = plugin.getQuestManager().getPlayerXP(player.getUniqueId());
        
        MessageUtils.sendMessage(player, "&6Your Mystic Information:");
        MessageUtils.sendMessage(player, "&eTotal Mystic XP: &f" + playerXP);
        MessageUtils.sendMessage(player, "&eActive Quests: &f" + quests.size());
        
        if (!quests.isEmpty()) {
            MessageUtils.sendMessage(player, "&6Active Quests:");
            for (Quest quest : quests) {
                MessageUtils.sendMessage(player, "&e- " + quest.getName() + " &7(" + quest.getProgress() + "/" + quest.getTargetAmount() + ")");
            }
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> commands = new ArrayList<>();
            commands.add("info");
            
            if (sender.hasPermission("mysticquest.admin")) {
                commands.add("reload");
                commands.add("spawnnpc");
                commands.add("resetxp");
            }
            
            String input = args[0].toLowerCase();
            for (String cmd : commands) {
                if (cmd.startsWith(input)) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("spawnnpc") && sender.hasPermission("mysticquest.admin")) {
                // Add NPC types from config
                if (plugin.getConfigManager().getConfig("npcs.yml") != null) {
                    Set<String> npcTypes = plugin.getConfigManager().getConfig("npcs.yml")
                            .getKeys(false);
                    
                    String input = args[1].toLowerCase();
                    completions.addAll(npcTypes.stream()
                            .filter(type -> type.toLowerCase().startsWith(input))
                            .collect(Collectors.toList()));
                }
            } else if (args[0].equalsIgnoreCase("resetxp") && sender.hasPermission("mysticquest.admin")) {
                // Add online player names
                String input = args[1].toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(input)) {
                        completions.add(player.getName());
                    }
                }
            }
        }
        
        return completions;
    }
} 