package me.zpleum.zmystic.quest.models;

import me.zpleum.zmystic.quest.MysticQuest;
import me.zpleum.zmystic.quest.config.ConfigManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

public class QuestNPC {

    private final MysticQuest plugin;
    private final String npcType;
    private final UUID targetPlayer;
    private Location location;
    private UUID uuid;
    private NPC npc;
    private String name;
    private List<String> questTypes;
    private boolean interacting = false;
    private boolean offeringQuest = false;
    private boolean hasNotifiedPlayer = false;
    private boolean waitingForResponse = false;
    private String questStory = "";
    private BukkitTask moveTask;
    private int actionBarTaskId = -1;

    // Default skin signature and texture for all black NPC
    private static final String DEFAULT_SKIN_SIGNATURE = "J4qwQIK5n9V5zNgMBL8vbIgKcm5yTlX1XWlGbokmnhEr3hE7+RKHxUCEYu+7+MeFAE7kuhLUDQjM77CV72S18B8OITbMsWFyfUzYbNZCG95jZBP5vjNE1TRcgGVUOrY+uCeV6DdzYNzSAynXgbHJLfHALNdBjmwWBh6J8jyWDf/p2IP7CsAFZLRdOqfJbv4AUJQsqzuhK15RPx9sBdgNZtZa46XZ5d4TJlEH/cU1LqYlwsUrV4G/+rGmngoHIjUGx8vy9OG+s2lfUpEaEpzgPcJzGYmJ6jLs9PF3fHhO0XTnSh35TtCiZ/MPnKvIIqoLEYSXMw2LxEm1M65fSnkgbS2Q9L1vT5s7YUMFWkBxXpqGqEWEyQTw1y03aLtaI5JAFPX9FGmDAMXnZ0QBMsWJ9oj8LSQ9yv+JDjPUOLDJazPlkwxkoUBCc56TRynKWIJsKGCNY6TVJ5jbPRzljgUaoI5IQ5FnvLcE9BEuWDcHBdkPfKaIyhpGBL6G5kLkDwMycbRHUkpjMGZ8ZGj66TkbBQFwOpuK7xTYGf+RL+fO9iJv6c8DWLI+5SiE9xY4PGGFEJOUy2oSKOJ5L8p+T6uAJYD/pLozsB9gnvyUBOIgK++Vv06L7vx5TQcITfQv5jgkYerP2FQ0TdTmJCdEOcXIuecMSWkSWB5hfVWVVfc="; 
    private static final String DEFAULT_SKIN_TEXTURE = "eyJ0aW1lc3RhbXAiOjE1OTE3ODY4NTU5MTgsInByb2ZpbGVJZCI6IjQxZDNhYmMyZDc0OTQwMGM5MDkwZDU0MzRkMDM4MzFhIiwicHJvZmlsZU5hbWUiOiJNZWdha2xvb24iLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzQxMmY4ODU2N2JkZTU5MmUyM2Y2MzZhZjVkMzFjZjVhM2YzODdlNWNkZjQzMGFkMWQxZGM5YjEzZGJmMmU2YjkifX19";

    // Store a map of frozen player data - UUID -> Original location
    private final Map<UUID, Location> frozenPlayers = new HashMap<>();

    public QuestNPC(MysticQuest plugin, String npcType, UUID targetPlayer, Location location) {
        this.plugin = plugin;
        this.npcType = npcType;
        this.targetPlayer = targetPlayer;
        this.location = location;
        this.uuid = UUID.randomUUID();

        loadFromConfig();
    }

    private void loadFromConfig() {
        FileConfiguration config = plugin.getConfigManager().getConfig(ConfigManager.NPCS_CONFIG);

        // Load NPC name
        name = config.getString(npcType + ".name", "Mysterious Figure");
        name = ChatColor.translateAlternateColorCodes('&', name);

        // Load quest types this NPC can offer
        questTypes = config.getStringList(npcType + ".quest-types");

        // Load quest story if available
        questStory = config.getString(npcType + ".story", "I have a secret quest for you. Are you brave enough to accept?");
    }

    public boolean spawn() {
        try {
            // Get Citizens NPC registry
            NPCRegistry registry = CitizensAPI.getNPCRegistry();

            // Create NPC with empty display name to avoid showing a name tag
            npc = registry.createNPC(EntityType.PLAYER, "");

            // Set the UUID so we can identify this NPC later
            this.uuid = npc.getUniqueId();

            // Configure NPC
            npc.data().set("zmystic-quest-npc", targetPlayer.toString());
            npc.data().set("zmystic-quest-npc-name", name); // Store the actual name internally

            // Hide name tag explicitly using all available methods
            npc.setUseMinecraftAI(false);
            npc.setProtected(true);
            npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, false);
            npc.data().setPersistent("nameplate-visible", false);

            // Set black skin from config
            FileConfiguration config = plugin.getConfigManager().getConfig(ConfigManager.MAIN_CONFIG);
            String skinSignature = config.getString("npc.skin.signature", "");
            String skinTexture = config.getString("npc.skin.texture", "");

            // Validate skin data by checking if they're proper Base64 strings
            // If not valid or empty, use defaults
            if (!isValidBase64(skinSignature) || !isValidBase64(skinTexture) || 
                skinSignature.isEmpty() || skinTexture.isEmpty()) {
                plugin.getLogger().info("Using default skin as config values are invalid or empty");
                skinSignature = DEFAULT_SKIN_SIGNATURE;
                skinTexture = DEFAULT_SKIN_TEXTURE;
            }

            // Apply skin
            SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
            skinTrait.setSkinPersistent("mystery", skinSignature, skinTexture);

            // Make NPC look at players
            LookClose lookCloseTrait = npc.getOrAddTrait(LookClose.class);
            lookCloseTrait.lookClose(true);
            lookCloseTrait.setRange(30); // Look at target from up to 30 blocks away
            lookCloseTrait.setRealisticLooking(true);

            // Spawn the NPC at the location
            npc.spawn(location);

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn Citizens NPC: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Check if a string is valid Base64 encoded data
     * @param str The string to check
     * @return true if valid Base64, false otherwise
     */
    private boolean isValidBase64(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        
        try {
            // Check if string contains only valid Base64 characters
            return str.matches("^[A-Za-z0-9+/]*={0,2}$");
        } catch (Exception e) {
            return false;
        }
    }

    public void despawn() {
        if (moveTask != null) {
            moveTask.cancel();
            moveTask = null;
        }

        if (npc != null && npc.isSpawned()) {
            npc.destroy();
        }
    }

    public void lookAt(Location target) {
        if (npc != null && npc.isSpawned()) {
            // Citizens NPCs with LookClose trait already handle this
            // but we can force it to look at a specific location

            // Get the direction vector
            Vector direction = target.toVector().subtract(npc.getEntity().getLocation().toVector()).normalize();

            // Set the entity's direction
            npc.getEntity().setRotation(
                    (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ())),
                    (float) Math.toDegrees(Math.asin(direction.getY()))
            );
        }
    }

    public void walkTo(Location target) {
        if (npc != null && npc.isSpawned()) {
            // Use Citizens navigation system
            npc.getNavigator().setTarget(target);
            npc.getNavigator().getLocalParameters().speedModifier(1.0f);

            // Update our location
            this.location = npc.getEntity().getLocation();
        }
    }

    public void walkBackwards(Player player) {
        if (npc != null && npc.isSpawned()) {
            // Calculate a location behind the NPC in the opposite direction of the player
            Vector direction = npc.getEntity().getLocation().toVector()
                    .subtract(player.getLocation().toVector()).normalize();

            Location targetLoc = npc.getEntity().getLocation().add(direction.multiply(5));

            // Use Citizens navigation or manual movement
            npc.getNavigator().setTarget(targetLoc);
            npc.getNavigator().getLocalParameters().speedModifier(0.8f);

            // Keep looking at player while walking backwards
            lookAt(player.getLocation());

            // Update our location
            this.location = npc.getEntity().getLocation();
        }
    }

    public void lockPlayerView(Player player) {
        // Store current player location if this is the first time freezing them
        if (!frozenPlayers.containsKey(player.getUniqueId())) {
            frozenPlayers.put(player.getUniqueId(), player.getLocation().clone());
        }

        // Make player look at NPC
        Location frozenLoc = frozenPlayers.get(player.getUniqueId());
        Vector direction = npc.getEntity().getLocation().toVector().subtract(frozenLoc.toVector()).normalize();

        // Create a new location that keeps position but changes direction to look at NPC
        Location lookLocation = new Location(
                frozenLoc.getWorld(),
                frozenLoc.getX(),
                frozenLoc.getY(),
                frozenLoc.getZ(),
                calculateYaw(direction),
                calculatePitch(direction)
        );

        // Force teleport player back to prevent any movement
        player.teleport(lookLocation);

        // Apply strong effects to prevent movement
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 100, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, 128, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 1, false, false));
        player.setWalkSpeed(0.0f);
    }

    public void unlockPlayerView(Player player) {
        // Clear frozen state and restore movement
        frozenPlayers.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.setWalkSpeed(0.2f); // Default walk speed

        // Ensure player is not immobilized by other means
        player.setFlySpeed(0.1f); // Default fly speed
        player.setInvulnerable(false); // Turn off invulnerability

        // Apply a small speed boost temporarily to ensure movement is restored
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 5, 1, false, false));
    }

    private float calculateYaw(Vector direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
    }

    private float calculatePitch(Vector direction) {
        return (float) Math.toDegrees(Math.asin(direction.getY()));
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        // Return the stored name rather than the NPC's display name (which is empty)
        return name;
    }

    public UUID getTargetPlayer() {
        return targetPlayer;
    }

    public Location getLocation() {
        return npc != null && npc.isSpawned() ? npc.getEntity().getLocation() : location;
    }

    public List<String> getQuestTypes() {
        return questTypes;
    }

    public String getNpcType() {
        return npcType;
    }

    public boolean isInteracting() {
        return interacting;
    }

    public void setInteracting(boolean interacting) {
        this.interacting = interacting;
    }

    public boolean isOfferingQuest() {
        return offeringQuest;
    }

    public void setOfferingQuest(boolean offeringQuest) {
        this.offeringQuest = offeringQuest;
    }

    public boolean hasNotifiedPlayer() {
        return hasNotifiedPlayer;
    }

    public void setHasNotifiedPlayer(boolean hasNotifiedPlayer) {
        this.hasNotifiedPlayer = hasNotifiedPlayer;
    }

    public boolean isWaitingForResponse() {
        return waitingForResponse;
    }

    public void setWaitingForResponse(boolean waitingForResponse) {
        this.waitingForResponse = waitingForResponse;
    }

    public String getQuestStory() {
        return questStory;
    }

    public int getActionBarTaskId() {
        return actionBarTaskId;
    }

    public void setActionBarTaskId(int actionBarTaskId) {
        this.actionBarTaskId = actionBarTaskId;
    }
} 