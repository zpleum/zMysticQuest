package me.zpleum.zmystic.quest.commands;

import me.zpleum.zmystic.quest.MysticQuest;
import me.zpleum.zmystic.quest.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class QuestResponseCommand implements CommandExecutor {

    private final MysticQuest plugin;
    private final boolean isAccept;
    private final boolean isUnstuck;

    public QuestResponseCommand(MysticQuest plugin, boolean isAccept) {
        this.plugin = plugin;
        this.isAccept = isAccept;
        this.isUnstuck = false;
    }

    public QuestResponseCommand(MysticQuest plugin, boolean isAccept, boolean isUnstuck) {
        this.plugin = plugin;
        this.isAccept = isAccept;
        this.isUnstuck = isUnstuck;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        // If this is the unstuck command, always reset player state
        if (isUnstuck) {
            resetPlayerState(player);
            return true;
        }

        // Check if player has a pending response
        if (!plugin.getNPCManager().hasPlayerPendingResponse(player.getUniqueId())) {
            return true; // Silently ignore if no pending response
        }

        // Handle accept or reject
        if (isAccept) {
            plugin.getNPCManager().handleQuestAccept(player);
        } else {
            plugin.getNPCManager().handleQuestReject(player);
        }

        return true;
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

        // Clear pending response if any
        if (plugin.getNPCManager().hasPlayerPendingResponse(player.getUniqueId())) {
            plugin.getNPCManager().clearPlayerPendingResponses(player.getUniqueId());
        }

        // Let player know they've been reset
        MessageUtils.sendMessage(player, "&aYou have been unstuck!");
    }
} 