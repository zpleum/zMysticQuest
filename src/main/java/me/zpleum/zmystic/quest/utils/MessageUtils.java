package me.zpleum.zmystic.quest.utils;

import me.zpleum.zmystic.quest.MysticQuest;
import me.zpleum.zmystic.quest.config.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class MessageUtils {
    
    public static void sendMessage(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) {
            return;
        }
        
        // Get prefix from config
        String prefix = MysticQuest.getInstance().getConfigManager()
                .getConfig(ConfigManager.MESSAGES_CONFIG)
                .getString("prefix", "");
        
        // Send colorized message with prefix
        player.sendMessage(colorize(prefix + message));
    }
    
    public static String colorize(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }
} 