package me.zpleum.zmystic.quest.listeners;

import me.zpleum.zmystic.quest.MysticQuest;
import me.zpleum.zmystic.quest.config.ConfigManager;
import me.zpleum.zmystic.quest.models.Quest;
import me.zpleum.zmystic.quest.models.QuestNPC;
import me.zpleum.zmystic.quest.models.QuestType;
import me.zpleum.zmystic.quest.utils.MessageUtils;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.MerchantRecipe;

import org.bukkit.StructureType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;

public class PlayerListener implements Listener {

    private final MysticQuest plugin;

    public PlayerListener(MysticQuest plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // If player had pending NPC interactions, clean them up
        if (plugin.getNPCManager().hasPlayerPendingResponse(playerId)) {
            plugin.getNPCManager().clearPlayerPendingResponses(playerId);
        }

        // Ensure player state is reset in case they log back in
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setInvulnerable(false);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.BLINDNESS);

        // Save player data when they quit
        plugin.getQuestManager().savePlayerData();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Reset some player states to avoid issues when rejoining after disconnecting during NPC interaction
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setInvulnerable(false);

        // Load player data when they join
        Set<Quest> quests = plugin.getQuestManager().getPlayerQuests(player.getUniqueId());

        // Check for expired quests
        quests.removeIf(quest -> {
            if (quest.isExpired()) {
                // Send expired message
                String message = "Your quest '" + quest.getName() + "' has expired.";
                MessageUtils.sendMessage(player, message);
                return true;
            }
            return false;
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // If the player has a pending NPC interaction, restrict movement more strictly
        if (plugin.getNPCManager().hasPlayerPendingResponse(player.getUniqueId())) {
            if (event.getFrom().distance(event.getTo()) > 0.2) {
                event.setCancelled(true);
                return;
            }
        }

        // Ignore small movements like head turning
        if (event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }

        Set<Quest> quests = plugin.getQuestManager().getPlayerQuests(player.getUniqueId());

        for (Quest quest : quests) {
            if (quest.getType() == QuestType.EXPLORE) {
                String targetBiome = (String) quest.getData().getOrDefault("biome", "");
                String targetStructure = (String) quest.getData().getOrDefault("structure", "");

                boolean biomeMatch = targetBiome.isEmpty() ||
                        player.getLocation().getBlock().getBiome().name().equalsIgnoreCase(targetBiome);

                boolean structureMatch = false;
                if (targetStructure.isEmpty()) {
                    structureMatch = true;
                } else {
                    StructureType structureType = getStructureTypeByName(targetStructure);
                    if (structureType != null && player.getLocation().getWorld().canGenerateStructures()) {
                        structureMatch = player.getLocation().getWorld().locateNearestStructure(
                                player.getLocation(), structureType, 100, false
                        ) != null;
                    }
                }

                if ((biomeMatch && targetStructure.isEmpty()) ||
                        (structureMatch && targetBiome.isEmpty()) ||
                        (biomeMatch && structureMatch)) {

                    plugin.getQuestManager().updateQuestProgress(quest, 1);

                    if (quest.isCompleted()) {
                        plugin.getQuestManager().completeQuest(quest, player);
                    }
                }
            }
        }
    }

    private StructureType getStructureTypeByName(String name) {
        for (Field field : StructureType.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == StructureType.class) {
                try {
                    StructureType structureType = (StructureType) field.get(null);
                    if (structureType.getName().equalsIgnoreCase(name)) {
                        return structureType;
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    @EventHandler
    public void onEntityKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        EntityType entityType = event.getEntityType();
        Set<Quest> quests = plugin.getQuestManager().getPlayerQuests(killer.getUniqueId());

        for (Quest quest : quests) {
            if (quest.getType() == QuestType.KILL) {
                String targetEntity = (String) quest.getData().get("entity");

                if (targetEntity != null && targetEntity.equalsIgnoreCase(entityType.name())) {
                    quest.incrementProgress();

                    // Send progress message
                    String message = "Quest progress: " + quest.getProgress() + "/" + quest.getTargetAmount();
                    MessageUtils.sendMessage(killer, message);

                    // Check if completed
                    if (quest.isCompleted()) {
                        plugin.getQuestManager().completeQuest(quest, killer);
                    } else {
                        // Update progress
                        plugin.getQuestManager().updateQuestProgress(quest, quest.getProgress());
                    }
                }
            }
        }
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        ItemStack crafted = event.getCurrentItem();
        
        if (crafted == null || crafted.getType() == Material.AIR) return;
        
        Set<Quest> quests = plugin.getQuestManager().getPlayerQuests(player.getUniqueId());
        
        for (Quest quest : quests) {
            if (quest.getType() == QuestType.CRAFT) {
                String targetItem = (String) quest.getData().get("item");
                
                if (targetItem != null && targetItem.equalsIgnoreCase(crafted.getType().name())) {
                    quest.incrementProgress();
                    
                    // Send progress message
                    String message = "Quest progress: " + quest.getProgress() + "/" + quest.getTargetAmount();
                    MessageUtils.sendMessage(player, message);
                    
                    // Check if completed
                    if (quest.isCompleted()) {
                        plugin.getQuestManager().completeQuest(quest, player);
                    } else {
                        // Update progress
                        plugin.getQuestManager().updateQuestProgress(quest, quest.getProgress());
                    }
                }
            }
        }
    }
    
    @EventHandler
    public void onEntityTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player)) return;
        
        Player player = (Player) event.getOwner();
        Entity entity = event.getEntity();
        
        handleInteractQuest(player, entity.getType().name(), "TAME");
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        // Ring bell
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && 
            event.getClickedBlock() != null && 
            event.getClickedBlock().getType() == Material.BELL) {
            
            handleInteractQuest(player, "BELL", "RING");
        }
        
        // Check for feeding parrots
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && 
            event.getItem() != null && 
            event.getItem().getType() == Material.WHEAT_SEEDS) {
            
            if (event.getClickedBlock() != null) {
                for (Entity entity : event.getClickedBlock().getWorld().getNearbyEntities(
                        event.getClickedBlock().getLocation(), 2, 2, 2)) {
                    if (entity.getType() == EntityType.PARROT) {
                        handleInteractQuest(player, "PARROT", "FEED");
                        break;
                    }
                }
            }
        }
    }
    
    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        
        Player player = event.getPlayer();
        Entity caught = event.getCaught();
        
        if (caught != null && caught.getType() == EntityType.SALMON) {
            handleInteractQuest(player, "SALMON", "FISH");
        }
    }
    
    @EventHandler
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        
        if (event.getBlockClicked() != null) {
            for (Entity entity : event.getBlockClicked().getWorld().getNearbyEntities(
                    event.getBlockClicked().getLocation(), 2, 2, 2)) {
                if (entity.getType() == EntityType.COW) {
                    handleInteractQuest(player, "COW", "MILK");
                    break;
                }
            }
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        
        // Prevent inventory interaction if player has a pending NPC response
        if (plugin.getNPCManager().hasPlayerPendingResponse(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        
        // Check for villager trading
        if (event.getView().getType() == InventoryType.MERCHANT && 
            event.getSlotType() == InventoryType.SlotType.RESULT) {
            
            handleInteractQuest(player, "VILLAGER", "TRADE");
        }
        
        // Normal collection quests
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        
        // Check collection quests
        Set<Quest> quests = plugin.getQuestManager().getPlayerQuests(player.getUniqueId());
        
        for (Quest quest : quests) {
            if (quest.getType() == QuestType.COLLECT) {
                String targetItem = (String) quest.getData().get("item");
                
                if (targetItem != null && targetItem.equalsIgnoreCase(clickedItem.getType().name())) {
                    int targetAmount = (int) quest.getData().getOrDefault("amount", 1);
                    int currentItemCount = 0;
                    
                    // Count all matching items in inventory
                    for (ItemStack item : player.getInventory().getContents()) {
                        if (item != null && item.getType().name().equalsIgnoreCase(targetItem)) {
                            currentItemCount += item.getAmount();
                        }
                    }
                    
                    // Update progress
                    plugin.getQuestManager().updateQuestProgress(quest, Math.min(currentItemCount, targetAmount));
                    
                    // Send progress message
                    String message = "Quest progress: " + quest.getProgress() + "/" + quest.getTargetAmount();
                    MessageUtils.sendMessage(player, message);
                    
                    // Check if completed
                    if (quest.isCompleted()) {
                        plugin.getQuestManager().completeQuest(quest, player);
                        
                        // Remove items if quest is complete
                        int remainingToRemove = targetAmount;
                        for (int i = 0; i < player.getInventory().getSize() && remainingToRemove > 0; i++) {
                            ItemStack item = player.getInventory().getItem(i);
                            if (item != null && item.getType().name().equalsIgnoreCase(targetItem)) {
                                int amountToRemove = Math.min(item.getAmount(), remainingToRemove);
                                remainingToRemove -= amountToRemove;
                                
                                if (amountToRemove == item.getAmount()) {
                                    player.getInventory().setItem(i, null);
                                } else {
                                    item.setAmount(item.getAmount() - amountToRemove);
                                }
                            }
                        }
                        player.updateInventory();
                    }
                }
            }
        }
    }
    
    /**
     * Helper method to handle INTERACT quest type progress updates
     */
    private void handleInteractQuest(Player player, String entityType, String interactionType) {
        Set<Quest> quests = plugin.getQuestManager().getPlayerQuests(player.getUniqueId());
        
        for (Quest quest : quests) {
            if (quest.getType() == QuestType.INTERACT) {
                String targetEntity = (String) quest.getData().get("entity");
                String targetInteraction = (String) quest.getData().get("interaction");
                
                if (targetEntity != null && targetEntity.equalsIgnoreCase(entityType) &&
                    targetInteraction != null && targetInteraction.equalsIgnoreCase(interactionType)) {
                    
                    quest.incrementProgress();
                    
                    // Send progress message
                    String message = "Quest progress: " + quest.getProgress() + "/" + quest.getTargetAmount();
                    MessageUtils.sendMessage(player, message);
                    
                    // Check if completed
                    if (quest.isCompleted()) {
                        plugin.getQuestManager().completeQuest(quest, player);
                    } else {
                        // Update progress
                        plugin.getQuestManager().updateQuestProgress(quest, quest.getProgress());
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Check if player has a pending quest offer
        if (plugin.getNPCManager().hasPlayerPendingResponse(player.getUniqueId())) {
            String message = event.getMessage().toLowerCase();

            // Handle chat-based responses as a fallback if commands aren't used
            // This is in addition to the /accept and /reject commands
            if (message.contains("accept") || message.contains("yes") || message.equals("y")) {
                event.setCancelled(true);
                // Need to run this on the main thread since we're in an async event
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getNPCManager().handleQuestAccept(player);
                });
            } else if (message.contains("reject") || message.contains("no") || message.equals("n")) {
                event.setCancelled(true);
                // Need to run this on the main thread since we're in an async event
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getNPCManager().handleQuestReject(player);
                });
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        // Check if entity is a quest NPC
        if (entity.getPersistentDataContainer().has(plugin.getQuestManager().getNPC_KEY(), PersistentDataType.STRING)) {
            String npcId = entity.getPersistentDataContainer().get(
                    plugin.getQuestManager().getNPC_KEY(),
                    PersistentDataType.STRING
            );

            if (npcId != null) {
                try {
                    UUID uuid = UUID.fromString(npcId);
                    QuestNPC npc = plugin.getNPCManager().getNPC(uuid);

                    if (npc != null) {
                        // If already waiting for response, don't re-trigger
                        if (npc.isWaitingForResponse()) {
                            event.setCancelled(true);
                            return;
                        }

                        // Trigger quest offer if NPC hasn't done so already
                        if (!npc.isOfferingQuest()) {
                            npc.setWaitingForResponse(true);
                            // This is now handled in NPCManager.offerQuestChoice

                            // Cancel the event to prevent normal interaction
                            event.setCancelled(true);
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // Invalid UUID, ignore
                }
            }
        }
    }
} 