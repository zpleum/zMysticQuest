package me.zpleum.zmystic.quest.managers;

import me.zpleum.zmystic.quest.MysticQuest;
import me.zpleum.zmystic.quest.config.ConfigManager;
import me.zpleum.zmystic.quest.models.QuestNPC;
import me.zpleum.zmystic.quest.utils.MessageUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class NPCManager {

    private final MysticQuest plugin;
    private final Map<UUID, QuestNPC> activeNPCs;
    private final Map<UUID, UUID> playerPendingResponses; // Player UUID -> NPC UUID
    private final Map<UUID, BukkitTask> storyAnimationTasks; // Player UUID -> Task
    private BukkitTask spawnTask;
    private BukkitTask approachTask;

    // Config values
    private int spawnInterval;
    private int despawnTime;
    private int maxDistance;
    private int approachDistance;
    private boolean notificationsEnabled;
    private String notificationSound;
    private boolean particlesEnabled;

    public NPCManager(MysticQuest plugin) {
        this.plugin = plugin;
        this.activeNPCs = new HashMap<>();
        this.playerPendingResponses = new ConcurrentHashMap<>();
        this.storyAnimationTasks = new HashMap<>();
        loadConfig();
    }

    private void loadConfig() {
        FileConfiguration config = plugin.getConfigManager().getConfig(ConfigManager.MAIN_CONFIG);
        spawnInterval = config.getInt("npc.spawn-interval", 300);
        despawnTime = config.getInt("npc.despawn-time", 60);
        maxDistance = config.getInt("npc.max-distance", 30);
        approachDistance = config.getInt("npc.approach-distance", 5);
        notificationsEnabled = config.getBoolean("npc.notification.enabled", true);
        notificationSound = config.getString("npc.notification.sound", "ENTITY_VILLAGER_AMBIENT");
        particlesEnabled = config.getBoolean("npc.notification.particles", true);
    }

    public void startSpawningTask() {
        // Cancel existing tasks if they're running
        stopSpawningTask();

        // Start new spawn task
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::attemptSpawnNPCs, 20L, spawnInterval * 20L);

        // Start approach task
        approachTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateNPCs, 10L, 10L);
    }

    public void stopSpawningTask() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }

        if (approachTask != null) {
            approachTask.cancel();
            approachTask = null;
        }

        // Despawn all active NPCs and unlock any players
        for (QuestNPC npc : new ArrayList<>(activeNPCs.values())) {
            // Find any player this NPC was targeting
            Player targetPlayer = Bukkit.getPlayer(npc.getTargetPlayer());
            if (targetPlayer != null && targetPlayer.isOnline()) {
                npc.unlockPlayerView(targetPlayer);
            }
            npc.despawn();
        }
        activeNPCs.clear();

        // Cancel any story animation tasks
        for (BukkitTask task : storyAnimationTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        storyAnimationTasks.clear();
        playerPendingResponses.clear();
    }

    private void attemptSpawnNPCs() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Check if player already has an NPC
            if (hasActiveNPCForPlayer(player)) {
                continue;
            }

            // Random chance to spawn
            if (ThreadLocalRandom.current().nextInt(100) < 30) { // 30% chance
                spawnNPCForPlayer(player);
            }
        }
    }

    private boolean hasActiveNPCForPlayer(Player player) {
        for (QuestNPC npc : activeNPCs.values()) {
            if (npc.getTargetPlayer().equals(player.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    // Make this method public for admin commands
    public void spawnNPCForPlayer(Player player) {
        // Get random NPC type from config
        String npcType = getRandomNPCType();
        if (npcType == null) return;

        spawnNPCForPlayer(player, npcType);
    }

    // Add a new method to spawn a specific NPC type
    public void spawnNPCForPlayer(Player player, String npcType) {
        // Check if NPC type exists in config
        FileConfiguration npcsConfig = plugin.getConfigManager().getConfig(ConfigManager.NPCS_CONFIG);
        if (!npcsConfig.contains(npcType)) {
            plugin.getLogger().warning("NPC type " + npcType + " does not exist in npcs.yml");
            return;
        }

        // Find spawn location exactly 10 blocks away from player
        Location spawnLoc = findSpawnLocation(player.getLocation(), 10);
        if (spawnLoc == null) {
            if (plugin.getConfigManager().getConfig(ConfigManager.MAIN_CONFIG).getBoolean("debug", false)) {
                plugin.getLogger().warning("Could not find a suitable spawn location for NPC near " + player.getName());
            }
            return;
        }

        // Create and spawn NPC
        QuestNPC npc = new QuestNPC(plugin, npcType, player.getUniqueId(), spawnLoc);
        boolean spawned = npc.spawn();

        if (spawned) {
            activeNPCs.put(npc.getUuid(), npc);

            // Schedule despawn with rejection animation
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (activeNPCs.containsKey(npc.getUuid())) {
                    handleTimeoutDespawn(player, npc);
                }
            }, despawnTime * 20L);

            if (plugin.getConfigManager().getConfig(ConfigManager.MAIN_CONFIG).getBoolean("debug", false)) {
                plugin.getLogger().info("Spawned NPC " + npc.getName() + " for player " + player.getName());
            }
        }
    }

    private String getRandomNPCType() {
        FileConfiguration npcsConfig = plugin.getConfigManager().getConfig(ConfigManager.NPCS_CONFIG);
        ConfigurationSection section = npcsConfig.getConfigurationSection("");
        if (section == null) return null;

        Set<String> npcTypes = section.getKeys(false);
        if (npcTypes.isEmpty()) return null;

        List<String> npcList = new ArrayList<>(npcTypes);
        int randomIndex = ThreadLocalRandom.current().nextInt(npcList.size());
        return npcList.get(randomIndex);
    }

    /**
     * Finds a spawn location exactly at the specified distance from the center
     * @param center The player's location
     * @param distance The exact distance to spawn the NPC
     * @return A suitable spawn location or null if none found
     */
    private Location findSpawnLocation(Location center, int distance) {
        int attempts = 0;
        while (attempts < 20) { // try more times to find a good spot
            // Generate a random angle
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);

            // Calculate position exactly at the specified distance
            int x = (int) (center.getX() + distance * Math.cos(angle));
            int z = (int) (center.getZ() + distance * Math.sin(angle));

            // Find safe Y
            Location loc = new Location(center.getWorld(), x, center.getY(), z);
            int y = center.getWorld().getHighestBlockYAt(x, z);
            loc.setY(y + 1);

            // Check if location is safe for spawn (air blocks and not in water/lava)
            if (loc.getBlock().getType().isAir() &&
                    loc.clone().add(0, 1, 0).getBlock().getType().isAir() &&
                    !loc.getBlock().isLiquid()) {

                // Set the yaw to face the player
                org.bukkit.util.Vector direction = center.toVector()
                        .subtract(loc.toVector())
                        .normalize();
                double yaw = Math.atan2(-direction.getX(), direction.getZ()) * (180 / Math.PI);
                loc.setYaw((float) yaw);

                return loc;
            }

            attempts++;
        }

        return null;
    }

    private void updateNPCs() {
        Iterator<Map.Entry<UUID, QuestNPC>> iterator = activeNPCs.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, QuestNPC> entry = iterator.next();
            QuestNPC npc = entry.getValue();

            // Check if target player is still online
            Player targetPlayer = Bukkit.getPlayer(npc.getTargetPlayer());
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                // Make sure to unlock player view if they disconnected
                if (targetPlayer != null) {
                    npc.unlockPlayerView(targetPlayer);
                }
                npc.despawn();
                iterator.remove();
                continue;
            }

            // Check distance between NPC and player
            double distance = npc.getLocation().distance(targetPlayer.getLocation());

            if (distance > maxDistance) {
                // Player moved too far away, use the timeout animation
                handleTimeoutDespawn(targetPlayer, npc);
                // Note: The animation will remove the NPC from activeNPCs
                // so we need to remove it from the iterator
                iterator.remove();
            } else if (!npc.isInteracting()) {
                // Start approaching player if not already doing so
                npc.setInteracting(true);
                approachPlayer(npc, targetPlayer);
            }

            // Lock player view if NPC is waiting for response
            if (npc.isWaitingForResponse() && distance <= 5) {
                npc.lockPlayerView(targetPlayer);
            } else if (!npc.isWaitingForResponse() && !npc.isOfferingQuest()) {
                // Restore player movement if no longer in conversation
                npc.unlockPlayerView(targetPlayer);
            }

            // Notification when NPC is getting closer
            if (notificationsEnabled && distance < maxDistance / 2 && !npc.hasNotifiedPlayer()) {
                notifyPlayer(targetPlayer);
                npc.setHasNotifiedPlayer(true);
            }
        }
    }

    private void approachPlayer(QuestNPC npc, Player player) {
        // Make NPC face player
        npc.lookAt(player.getLocation());

        // Start a task to make NPC gradually approach the player more naturally
        final int[] stepCount = {0};
        final int[] taskId = {0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Check if NPC still exists and player is still online
            if (!activeNPCs.containsKey(npc.getUuid()) || !player.isOnline()) {
                Bukkit.getScheduler().cancelTask(taskId[0]);
                return;
            }

            // Always make NPC face the player
            npc.lookAt(player.getLocation());

            // Calculate distance
            double distance = npc.getLocation().distance(player.getLocation());

            // If we're close enough, stop approaching
            if (distance <= 2 || stepCount[0] >= 30) {
                Bukkit.getScheduler().cancelTask(taskId[0]);

                // If close enough, offer the quest
                if (distance <= 2 && !npc.isOfferingQuest() && !npc.isWaitingForResponse()) {
                    npc.setWaitingForResponse(true);
                    offerQuestChoice(npc, player);
                }
                return;
            }

            // Make NPC walk toward player with slight randomization for more natural movement
            Location targetLoc = player.getLocation().clone();

            // Add a very slight random movement to make it look more natural
            if (stepCount[0] % 3 == 0) {
                double offsetX = (Math.random() - 0.5) * 0.5;
                double offsetZ = (Math.random() - 0.5) * 0.5;
                targetLoc.add(offsetX, 0, offsetZ);
            }

            // Move the NPC
            npc.walkTo(targetLoc);

            // Add mystical particles
            Location particleLoc = npc.getLocation().add(0, 1, 0);

            // Different particle effects based on NPC type or step count
            if (stepCount[0] % 5 == 0) {
                // Main particle trail
                particleLoc.getWorld().spawnParticle(
                        Particle.END_ROD,
                        particleLoc,
                        5, 0.2, 0.5, 0.2, 0.01
                );

                // Shadow effect
                particleLoc.getWorld().spawnParticle(
                        Particle.SMOKE,
                        particleLoc.clone().add(0, -0.5, 0),
                        3, 0.3, 0.1, 0.3, 0.01
                );
            }

            // Sporadic mystical particles
            if (stepCount[0] % 8 == 0) {
                particleLoc.getWorld().spawnParticle(
                        Particle.DRAGON_BREATH,
                        particleLoc,
                        2, 0.2, 0.2, 0.2, 0.01
                );

                // Play subtle sound effect occasionally
                if (stepCount[0] % 16 == 0) {
                    player.playSound(particleLoc, Sound.BLOCK_BEACON_AMBIENT, 0.5f, 1.5f);
                }
            }

            stepCount[0]++;
        }, 10L, 10L); // Run every 10 ticks (0.5 seconds)

        taskId[0] = task.getTaskId();
    }

    private void offerQuestChoice(QuestNPC npc, Player player) {
        // Store pending response
        playerPendingResponses.put(player.getUniqueId(), npc.getUuid());

        // Get quest story
        String story = npc.getQuestStory();
        final int[] charIndex = {0};
        final int[] taskId = {0};
        final boolean[] showingChoices = {false};
        final int[] choiceDelay = {0};

        // Create a typing animation for the story first
        BukkitTask animationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // If player no longer has a pending response, cancel the task
            if (!playerPendingResponses.containsKey(player.getUniqueId())) {
                Bukkit.getScheduler().cancelTask(taskId[0]);
                return;
            }

            if (!showingChoices[0]) {
                // Still showing the question with typing animation
                if (charIndex[0] <= story.length()) {
                    // Show current portion of text
                    String displayText = story.substring(0, charIndex[0]);
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText("§6" + npc.getName() + ": §f" + displayText));
                    charIndex[0]++;

                    // Play typing sound every few characters
                    if (charIndex[0] % 3 == 0) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.2f, 1.5f);
                    }
                } else {
                    // Question animation completed, increment delay counter
                    choiceDelay[0]++;

                    // Keep showing full text during waiting period
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText("§6" + npc.getName() + ": §f" + story));

                    // After 5 seconds (100 ticks), switch to showing choices
                    if (choiceDelay[0] >= 100) {
                        showingChoices[0] = true;
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.2f);
                    }
                }
            } else {
                // For choices, check if story is too long
                String message;

                // Limit message length to avoid ActionBar cutoff (typically around 60-80 chars)
                if (story.length() > 40) {
                    // Truncate the story if needed
                    String truncatedStory = story.substring(0, Math.min(40, story.length())) + "...";
                    message = "§6" + npc.getName() + ": §f" + truncatedStory + " §a/accept §7or §c/reject";
                } else {
                    // Keep full story if short enough
                    message = "§6" + npc.getName() + ": §f" + story + " §a/accept §7or §c/reject";
                }

                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
            }
        }, 0L, 2L); // Every 2 ticks for smoother typing animation

        // Store task ID in NPC to cancel later if needed
        taskId[0] = animationTask.getTaskId();
        npc.setActionBarTaskId(animationTask.getTaskId());

        // Play sound
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1.0f, 0.8f);
    }

    public void handleQuestAccept(Player player) {
        UUID npcUuid = playerPendingResponses.get(player.getUniqueId());
        if (npcUuid == null) {
            return; // No pending response
        }

        QuestNPC npc = activeNPCs.get(npcUuid);
        if (npc == null) {
            return; // NPC no longer exists
        }

        // Remove pending response
        playerPendingResponses.remove(player.getUniqueId());

        // Cancel action bar task if it exists
        if (npc.getActionBarTaskId() > 0) {
            Bukkit.getScheduler().cancelTask(npc.getActionBarTaskId());
            npc.setActionBarTaskId(-1);
        }

        // Set offering quest
        npc.setOfferingQuest(true);

        // Start story animation (keep player frozen during story)
        animateQuestStory(player, npc);
    }

    public void handleQuestReject(Player player) {
        UUID npcUuid = playerPendingResponses.get(player.getUniqueId());
        if (npcUuid == null) {
            return; // No pending response
        }

        QuestNPC npc = activeNPCs.get(npcUuid);
        if (npc == null) {
            return; // NPC no longer exists
        }

        // Remove pending response
        playerPendingResponses.remove(player.getUniqueId());

        // Cancel action bar task if it exists
        if (npc.getActionBarTaskId() > 0) {
            Bukkit.getScheduler().cancelTask(npc.getActionBarTaskId());
            npc.setActionBarTaskId(-1);
        }

        // Get rejection message
        String rejectionMessage = plugin.getConfigManager().getConfig(ConfigManager.MESSAGES_CONFIG)
                .getString("npc.quest-declined", "*The figure looks disappointed and walks away*");

        // Show message in ActionBar
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText("§c" + rejectionMessage));

        // Also send in chat for reference
        MessageUtils.sendMessage(player, "&c" + rejectionMessage);

        // Play sound effect
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.8f);

        // Reset NPC state
        npc.setWaitingForResponse(false);
        npc.setOfferingQuest(false);

        // Unlock player view (fully restore movement)
        npc.unlockPlayerView(player);

        // Make NPC walk backwards
        animateNPCRejection(player, npc);
    }

    private void animateQuestStory(Player player, QuestNPC npc) {
        // Reset any pending animations for this player
        if (storyAnimationTasks.containsKey(player.getUniqueId())) {
            storyAnimationTasks.get(player.getUniqueId()).cancel();
            storyAnimationTasks.remove(player.getUniqueId());
        }

        // For story animation after accepting, we'll use a different text
        FileConfiguration messagesConfig = plugin.getConfigManager().getConfig(ConfigManager.MESSAGES_CONFIG);
        String acceptStory = messagesConfig.getString("npc.quest-accepted-story",
                "Thank you for accepting. Your quest awaits...");

        final int[] charIndex = {0};
        final int[] taskId = {0};

        // Notify player with a sound
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);

        // Start new animation task
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Check if player is still online
            if (!player.isOnline()) {
                Bukkit.getScheduler().cancelTask(taskId[0]);
                storyAnimationTasks.remove(player.getUniqueId());
                return;
            }

            if (charIndex[0] <= acceptStory.length()) {
                String displayText = acceptStory.substring(0, charIndex[0]);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§6" + npc.getName() + ": §f" + displayText));
                charIndex[0]++;

                // Play typing sound every few characters
                if (charIndex[0] % 3 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.2f, 1.5f);
                }

                // Keep player locked
                npc.lockPlayerView(player);
            } else {
                // Story finished, wait a bit then give quest
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // Final check to ensure player is online
                    if (player.isOnline()) {
                        giveQuest(npc, player);
                        // Unlock player view after giving quest
                        npc.unlockPlayerView(player);
                    } else {
                        // If player went offline, make sure to clean up
                        if (activeNPCs.containsKey(npc.getUuid())) {
                            activeNPCs.remove(npc.getUuid());
                            npc.despawn();
                        }
                    }
                }, 40L); // Wait 2 seconds after story finishes

                // Cancel animation task
                Bukkit.getScheduler().cancelTask(taskId[0]);
                storyAnimationTasks.remove(player.getUniqueId());
            }
        }, 2L, 2L); // Run every 2 ticks (0.1 seconds)

        taskId[0] = task.getTaskId();
        storyAnimationTasks.put(player.getUniqueId(), task);
    }

    private void animateNPCRejection(Player player, QuestNPC npc) {
        // Make NPC walk backwards
        final int[] stepCount = {0};
        final int[] taskId = {0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (stepCount[0] < 10) {
                // Make NPC walk backwards
                npc.walkBackwards(player);

                // Enhanced darkness effects
                Location loc = npc.getLocation().add(0, 1, 0);

                // Main smoke effect
                loc.getWorld().spawnParticle(
                        Particle.LARGE_SMOKE,
                        loc,
                        8, 0.3, 0.5, 0.3, 0.01
                );

                // Add some soul particles
                if (stepCount[0] % 2 == 0) {
                    loc.getWorld().spawnParticle(
                            Particle.SOUL,
                            loc.clone().add(0, 0.5, 0),
                            3, 0.2, 0.2, 0.2, 0.02
                    );
                }

                // Play eerie sound
                if (stepCount[0] == 0 || stepCount[0] == 5) {
                    player.playSound(loc, Sound.ENTITY_ENDERMAN_AMBIENT, 0.5f, 0.5f);
                }

                stepCount[0]++;
            } else {
                // End of animation
                Bukkit.getScheduler().cancelTask(taskId[0]);

                // Create final disappearance effect
                createDespawnEffects(npc.getLocation());

                // Final check to ensure player movement is restored
                if (player.isOnline()) {
                    npc.unlockPlayerView(player);
                }

                // Remove NPC
                activeNPCs.remove(npc.getUuid());
                npc.despawn();
            }
        }, 5L, 5L); // Every 0.25 seconds

        taskId[0] = task.getTaskId();
    }

    private void giveQuest(QuestNPC npc, Player player) {
        // Give quest scroll
        plugin.getQuestManager().offerRandomQuest(player, npc);

        // Send acceptance message one last time in ActionBar
        String completionMessage = plugin.getConfigManager().getConfig(ConfigManager.MESSAGES_CONFIG)
                .getString("npc.quest-accepted", "*Quest accepted*");

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText("§a" + completionMessage));

        // Also send in chat for reference
        MessageUtils.sendMessage(player, "&a" + completionMessage);

        // Make sure to fully unlock player first
        npc.setWaitingForResponse(false);
        npc.setOfferingQuest(false);
        npc.unlockPlayerView(player);

        // Schedule NPC to despawn with effects
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (activeNPCs.containsKey(npc.getUuid())) {
                // Apply despawn effects
                createDespawnEffects(npc.getLocation());

                // Make NPC vanish
                npc.despawn();
                activeNPCs.remove(npc.getUuid());
            }
        }, 60L); // 3 seconds delay
    }

    private void notifyPlayer(Player player) {
        if (notificationsEnabled) {
            // Play sound
            try {
                Sound sound = Sound.valueOf(notificationSound);
                player.playSound(player.getLocation(), sound, 0.5f, 1.0f);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound specified in config: " + notificationSound);
            }

            // Show particles
            if (particlesEnabled) {
                player.spawnParticle(Particle.DRAGON_BREATH, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
            }
        }
    }

    public void removeNPC(UUID npcUuid) {
        QuestNPC npc = activeNPCs.remove(npcUuid);
        if (npc != null) {
            npc.despawn();
        }
    }

    public QuestNPC getNPC(UUID npcUuid) {
        return activeNPCs.get(npcUuid);
    }

    public Collection<QuestNPC> getActiveNPCs() {
        return activeNPCs.values();
    }

    public boolean hasPlayerPendingResponse(UUID playerId) {
        return playerPendingResponses.containsKey(playerId);
    }

    /**
     * Clears any pending response for a player, used by admin commands
     * @param playerId The player's UUID
     */
    public void clearPlayerPendingResponses(UUID playerId) {
        UUID npcUuid = playerPendingResponses.remove(playerId);

        // If the player had a pending response, also update the associated NPC
        if (npcUuid != null) {
            QuestNPC npc = activeNPCs.get(npcUuid);
            if (npc != null) {
                npc.setWaitingForResponse(false);
                npc.setOfferingQuest(false);
            }
        }

        // Cancel any story animation task
        BukkitTask task = storyAnimationTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Handles an NPC despawning due to timeout with same effects as rejection
     */
    private void handleTimeoutDespawn(Player player, QuestNPC npc) {
        // If player is already in conversation with the NPC, don't interrupt
        if (npc.isWaitingForResponse() || npc.isOfferingQuest()) {
            return;
        }

        // Same as handleQuestReject but for timeout scenario

        // Remove any pending response
        playerPendingResponses.remove(player.getUniqueId());

        // Cancel action bar task if it exists
        if (npc.getActionBarTaskId() > 0) {
            Bukkit.getScheduler().cancelTask(npc.getActionBarTaskId());
            npc.setActionBarTaskId(-1);
        }

        // Get timeout message (use the same as reject)
        String timeoutMessage = plugin.getConfigManager().getConfig(ConfigManager.MESSAGES_CONFIG)
                .getString("npc.quest-timeout", "*The mysterious figure loses interest and disappears into the shadows*");

        // Show message in ActionBar
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText("§c" + timeoutMessage));

        // Also send in chat for reference
        MessageUtils.sendMessage(player, "&c" + timeoutMessage);

        // Play sound effect
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.8f);

        // Reset NPC state
        npc.setWaitingForResponse(false);
        npc.setOfferingQuest(false);

        // Unlock player view (fully restore movement)
        npc.unlockPlayerView(player);

        // Use the same rejection animation
        animateNPCRejection(player, npc);
    }

    // Helper method to create despawn effects
    private void createDespawnEffects(Location location) {
        Location loc = location.add(0, 1, 0);

        // Enhanced disappearance effect
        // First effect - smoke
        loc.getWorld().spawnParticle(
                Particle.LARGE_SMOKE,
                loc,
                30, 0.5, 0.8, 0.5, 0.05
        );

        // Second effect - magical particles
        loc.getWorld().spawnParticle(
                Particle.END_ROD,
                loc,
                20, 0.3, 0.5, 0.3, 0.1
        );

        // Third effect - enchanting glyphs
        loc.getWorld().spawnParticle(
                Particle.ENCHANT,
                loc,
                50, 0.5, 0.8, 0.5, 1.0
        );

        // Play dramatic sound
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(loc.getWorld()) &&
                    player.getLocation().distance(loc) <= 16) {
                player.playSound(loc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.7f, 0.8f);
                player.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
            }
        }
    }
} 