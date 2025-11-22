package me.zpleum.zmystic.quest.managers;

import me.zpleum.zmystic.quest.MysticQuest;
import me.zpleum.zmystic.quest.config.ConfigManager;
import me.zpleum.zmystic.quest.models.Quest;
import me.zpleum.zmystic.quest.models.QuestNPC;
import me.zpleum.zmystic.quest.models.QuestType;
import me.zpleum.zmystic.quest.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class QuestManager {

    private final MysticQuest plugin;
    private final Map<UUID, Set<Quest>> playerQuests;
    private final Map<UUID, Integer> playerXP;

    // NamespacedKeys for persistent data
    private final NamespacedKey QUEST_KEY;
    private final NamespacedKey NPC_KEY;

    // Config values
    private String questItemMaterial;
    private boolean questItemGlow;
    private int maxActiveQuests;

    public QuestManager(MysticQuest plugin) {
        this.plugin = plugin;
        this.playerQuests = new HashMap<>();
        this.playerXP = new HashMap<>();

        // Initialize keys
        QUEST_KEY = new NamespacedKey(plugin, "quest_id");
        NPC_KEY = new NamespacedKey(plugin, "npc_id");

        // Load config values
        loadConfig();

        // Load player data
        loadPlayerData();
    }

    private void loadConfig() {
        FileConfiguration config = plugin.getConfigManager().getConfig(ConfigManager.MAIN_CONFIG);
        questItemMaterial = config.getString("quest.quest-item.material", "PAPER");
        questItemGlow = config.getBoolean("quest.quest-item.glow", true);
        maxActiveQuests = config.getInt("quest.max-active-quests", 3);
    }

    public void offerRandomQuest(Player player, QuestNPC npc) {
        // Check if player has max quests
        if (getPlayerQuests(player.getUniqueId()).size() >= maxActiveQuests) {
            // Player has too many active quests
            String message = "You already have too many active quests.";
            MessageUtils.sendMessage(player, message);
            return;
        }

        // Get available quest types for this NPC
        List<String> availableQuestTypes = npc.getQuestTypes();
        if (availableQuestTypes.isEmpty()) {
            plugin.getLogger().warning("NPC " + npc.getName() + " has no quest types configured!");
            return;
        }

        // Randomly select a quest type
        String questTypeId = availableQuestTypes.get(ThreadLocalRandom.current().nextInt(availableQuestTypes.size()));

        // Create the quest
        Quest quest = createQuestFromConfig(questTypeId, player.getUniqueId());
        if (quest == null) {
            plugin.getLogger().warning("Failed to create quest from type: " + questTypeId);
            return;
        }

        // Give quest item to player
        ItemStack questItem = createQuestItem(quest);
        player.getInventory().addItem(questItem);

        // Add quest to player's active quests
        addQuestToPlayer(player.getUniqueId(), quest);

        // Send quest received message
        String message = plugin.getConfigManager().getConfig(ConfigManager.MESSAGES_CONFIG)
                .getString("quest.received", "You have received a secret quest scroll!");
        MessageUtils.sendMessage(player, message);
    }

    private Quest createQuestFromConfig(String questTypeId, UUID playerId) {
        FileConfiguration questsConfig = plugin.getConfigManager().getConfig(ConfigManager.QUESTS_CONFIG);

        if (!questsConfig.contains(questTypeId)) {
            return null;
        }

        try {
            String type = questsConfig.getString(questTypeId + ".type");
            QuestType questType = QuestType.valueOf(type);

            String name = questsConfig.getString(questTypeId + ".name");
            String description = questsConfig.getString(questTypeId + ".description");
            int xpReward = questsConfig.getInt(questTypeId + ".xp");

            // Get specific data based on quest type
            Map<String, Object> questData = new HashMap<>();

            switch (questType) {
                case KILL:
                    questData.put("entity", questsConfig.getString(questTypeId + ".entity"));
                    questData.put("amount", questsConfig.getInt(questTypeId + ".amount"));
                    break;

                case COLLECT:
                    questData.put("item", questsConfig.getString(questTypeId + ".item"));
                    questData.put("amount", questsConfig.getInt(questTypeId + ".amount"));
                    break;

                case EXPLORE:
                    questData.put("biome", questsConfig.getString(questTypeId + ".biome", ""));
                    questData.put("structure", questsConfig.getString(questTypeId + ".structure", ""));
                    break;
                    
                case CRAFT:
                    questData.put("item", questsConfig.getString(questTypeId + ".item"));
                    questData.put("amount", questsConfig.getInt(questTypeId + ".amount", 1));
                    break;
                    
                case INTERACT:
                    questData.put("entity", questsConfig.getString(questTypeId + ".entity"));
                    questData.put("interaction", questsConfig.getString(questTypeId + ".interaction"));
                    questData.put("amount", questsConfig.getInt(questTypeId + ".amount", 1));
                    break;
            }

            // Optional time limit
            if (questsConfig.contains(questTypeId + ".time-limit")) {
                questData.put("timeLimit", questsConfig.getInt(questTypeId + ".time-limit"));
            }

            // Create quest with unique ID
            return new Quest(
                    UUID.randomUUID(),
                    questTypeId,
                    name,
                    description,
                    questType,
                    questData,
                    xpReward,
                    playerId
            );

        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid quest type for quest: " + questTypeId);
            return null;
        }
    }

    private ItemStack createQuestItem(Quest quest) {
        Material material;
        try {
            material = Material.valueOf(questItemMaterial);
        } catch (IllegalArgumentException e) {
            material = Material.PAPER;
            plugin.getLogger().warning("Invalid material in config: " + questItemMaterial + ". Using PAPER instead.");
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§6Secret Quest Scroll");

            List<String> lore = new ArrayList<>();
            lore.add("§7A mysterious scroll containing a secret quest.");
            lore.add("§7Only you can see its contents.");
            lore.add("§8");
            lore.add("§eQuest: §f" + quest.getName());
            lore.add("§eObjective: §f" + quest.getDescription());
            lore.add("§eReward: §f" + quest.getXpReward() + " Mystic XP");

            meta.setLore(lore);

            // Store quest ID in item
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(QUEST_KEY, PersistentDataType.STRING, quest.getId().toString());

            // Add glow effect if enabled
            if (questItemGlow) {
                // ใช้ Unbreaking แทน Durability
                // meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    public void completeQuest(Quest quest, Player player) {
        // Remove quest from player's active quests
        removeQuestFromPlayer(player.getUniqueId(), quest);

        // Award XP
        addXP(player.getUniqueId(), quest.getXpReward());

        // Send completion message
        String message = plugin.getConfigManager().getConfig(ConfigManager.MESSAGES_CONFIG)
                .getString("quest.completed", "You have completed the quest: %quest_name%!")
                .replace("%quest_name%", quest.getName());
        MessageUtils.sendMessage(player, message);

        // Show XP gained message
        String xpMessage = plugin.getConfigManager().getConfig(ConfigManager.MESSAGES_CONFIG)
                .getString("xp.gained", "+%xp% Mystic XP!")
                .replace("%xp%", String.valueOf(quest.getXpReward()));
        MessageUtils.sendMessage(player, xpMessage);

        // Show total XP message
        String totalXPMessage = plugin.getConfigManager().getConfig(ConfigManager.MESSAGES_CONFIG)
                .getString("xp.total", "You now have %xp% Mystic XP!")
                .replace("%xp%", String.valueOf(getPlayerXP(player.getUniqueId())));
        MessageUtils.sendMessage(player, totalXPMessage);

        // Check for reward unlocks
        checkRewardUnlocks(player);

        // Remove quest item from inventory
        removeQuestItemFromInventory(player, quest.getId());

        // Save player data
        savePlayerData();
    }

    private void checkRewardUnlocks(Player player) {
        int playerXP = getPlayerXP(player.getUniqueId());
        FileConfiguration rewardsConfig = plugin.getConfigManager().getConfig(ConfigManager.REWARDS_CONFIG);
        ConfigurationSection rewardsSection = rewardsConfig.getConfigurationSection("rewards");

        if (rewardsSection == null) return;

        // Sort tiers by XP required
        List<String> tiers = new ArrayList<>(rewardsSection.getKeys(false));
        tiers.sort((t1, t2) ->
            rewardsConfig.getInt("rewards." + t1 + ".xp-required") -
            rewardsConfig.getInt("rewards." + t2 + ".xp-required")
        );

        // Check each tier
        for (String tier : tiers) {
            int xpRequired = rewardsConfig.getInt("rewards." + tier + ".xp-required");

            // Check if player has newly unlocked this tier
            if (playerXP >= xpRequired && !hasUnlockedReward(player.getUniqueId(), tier)) {
                // Mark as unlocked
                setRewardUnlocked(player.getUniqueId(), tier, true);

                // Send unlock message
                String message = plugin.getConfigManager().getConfig(ConfigManager.MESSAGES_CONFIG)
                        .getString("xp.reward-unlocked", "You have unlocked a new reward tier: %tier%!")
                        .replace("%tier%", tier);
                MessageUtils.sendMessage(player, message);

                // Execute reward commands
                List<String> commands = rewardsConfig.getStringList("rewards." + tier + ".commands");
                for (String command : commands) {
                    command = command.replace("%player%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
            }
        }
    }

    private boolean hasUnlockedReward(UUID playerId, String tier) {
        File playerFile = getPlayerFile(playerId);
        if (!playerFile.exists()) return false;

        YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
        return playerConfig.getBoolean("rewards." + tier + ".unlocked", false);
    }

    private void setRewardUnlocked(UUID playerId, String tier, boolean unlocked) {
        File playerFile = getPlayerFile(playerId);
        YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

        playerConfig.set("rewards." + tier + ".unlocked", unlocked);
        playerConfig.set("rewards." + tier + ".unlocked-date", new Date().getTime());

        try {
            playerConfig.save(playerFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player reward data: " + e.getMessage());
        }
    }

    private void removeQuestItemFromInventory(Player player, UUID questId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isQuestItem(item, questId)) {
                player.getInventory().remove(item);
                return;
            }
        }
    }

    private boolean isQuestItem(ItemStack item, UUID questId) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (!container.has(QUEST_KEY, PersistentDataType.STRING)) return false;

        String storedQuestId = container.get(QUEST_KEY, PersistentDataType.STRING);
        return storedQuestId != null && storedQuestId.equals(questId.toString());
    }

    public void updateQuestProgress(Quest quest, int progress) {
        quest.setProgress(progress);

        // Check if quest is completed
        if (progress >= quest.getTargetAmount()) {
            Player player = Bukkit.getPlayer(quest.getPlayerId());
            if (player != null && player.isOnline()) {
                completeQuest(quest, player);
            }
        }

        // Save player data
        savePlayerData();
    }

    public Set<Quest> getPlayerQuests(UUID playerId) {
        return playerQuests.getOrDefault(playerId, new HashSet<>());
    }

    public void addQuestToPlayer(UUID playerId, Quest quest) {
        playerQuests.computeIfAbsent(playerId, k -> new HashSet<>()).add(quest);
        savePlayerData();
    }

    public void removeQuestFromPlayer(UUID playerId, Quest quest) {
        Set<Quest> quests = playerQuests.get(playerId);
        if (quests != null) {
            quests.remove(quest);
            savePlayerData();
        }
    }

    public int getPlayerXP(UUID playerId) {
        return playerXP.getOrDefault(playerId, 0);
    }

    public void addXP(UUID playerId, int amount) {
        playerXP.put(playerId, getPlayerXP(playerId) + amount);
        savePlayerData();
    }

    public void savePlayerData() {
        // Create player data directory if it doesn't exist
        File playerDataDir = new File(plugin.getDataFolder(), "playerdata");
        if (!playerDataDir.exists()) {
            playerDataDir.mkdirs();
        }

        // Save each player's data
        for (UUID playerId : playerQuests.keySet()) {
            savePlayerData(playerId);
        }

        // Save XP data for players who might not have active quests
        for (UUID playerId : playerXP.keySet()) {
            if (!playerQuests.containsKey(playerId)) {
                savePlayerData(playerId);
            }
        }
    }

    private void savePlayerData(UUID playerId) {
        File playerFile = getPlayerFile(playerId);
        YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

        // Save quests
        Set<Quest> quests = playerQuests.getOrDefault(playerId, new HashSet<>());
        List<Map<String, Object>> questsList = new ArrayList<>();

        for (Quest quest : quests) {
            Map<String, Object> questMap = new HashMap<>();
            questMap.put("id", quest.getId().toString());
            questMap.put("type_id", quest.getTypeId());
            questMap.put("name", quest.getName());
            questMap.put("description", quest.getDescription());
            questMap.put("quest_type", quest.getType().name());
            questMap.put("quest_data", quest.getData());
            questMap.put("xp_reward", quest.getXpReward());
            questMap.put("progress", quest.getProgress());
            questMap.put("start_time", quest.getStartTime());

            questsList.add(questMap);
        }

        playerConfig.set("quests", questsList);

        // Save XP
        playerConfig.set("mystic_xp", getPlayerXP(playerId));

        // Save file
        try {
            playerConfig.save(playerFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player data: " + e.getMessage());
        }
    }

    private void loadPlayerData() {
        File playerDataDir = new File(plugin.getDataFolder(), "playerdata");
        if (!playerDataDir.exists()) {
            return;
        }

        File[] playerFiles = playerDataDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (playerFiles == null) return;

        for (File file : playerFiles) {
            String fileName = file.getName();
            try {
                UUID playerId = UUID.fromString(fileName.substring(0, fileName.length() - 4));
                loadPlayerData(playerId);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid player data file name: " + fileName);
            }
        }
    }

    private void loadPlayerData(UUID playerId) {
        File playerFile = getPlayerFile(playerId);
        if (!playerFile.exists()) return;

        YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

        // Load quests
        List<?> questsList = playerConfig.getList("quests");
        Set<Quest> quests = new HashSet<>();

        if (questsList != null) {
            for (Object obj : questsList) {
                if (obj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> questMap = (Map<String, Object>) obj;

                    try {
                        UUID questId = UUID.fromString((String) questMap.get("id"));
                        String typeId = (String) questMap.get("type_id");
                        String name = (String) questMap.get("name");
                        String description = (String) questMap.get("description");
                        QuestType questType = QuestType.valueOf((String) questMap.get("quest_type"));
                        @SuppressWarnings("unchecked")
                        Map<String, Object> questData = (Map<String, Object>) questMap.get("quest_data");
                        int xpReward = ((Number) questMap.get("xp_reward")).intValue();
                        int progress = questMap.containsKey("progress") ? ((Number) questMap.get("progress")).intValue() : 0;
                        long startTime = questMap.containsKey("start_time") ? ((Number) questMap.get("start_time")).longValue() : System.currentTimeMillis();

                        Quest quest = new Quest(questId, typeId, name, description, questType, questData, xpReward, playerId);
                        quest.setProgress(progress);
                        quest.setStartTime(startTime);

                        quests.add(quest);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to load quest: " + e.getMessage());
                    }
                }
            }
        }

        playerQuests.put(playerId, quests);

        // Load XP
        playerXP.put(playerId, playerConfig.getInt("mystic_xp", 0));
    }

    private File getPlayerFile(UUID playerId) {
        return new File(plugin.getDataFolder(), "playerdata/" + playerId.toString() + ".yml");
    }

    public Quest getQuestById(UUID questId) {
        for (Set<Quest> quests : playerQuests.values()) {
            for (Quest quest : quests) {
                if (quest.getId().equals(questId)) {
                    return quest;
                }
            }
        }
        return null;
    }

    public NamespacedKey getQUEST_KEY() {
        return QUEST_KEY;
    }

    public NamespacedKey getNPC_KEY() {
        return NPC_KEY;
    }
} 