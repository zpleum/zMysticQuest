package me.zpleum.zmystic.quest.config;

import me.zpleum.zmystic.quest.MysticQuest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    
    private final MysticQuest plugin;
    private final Map<String, FileConfiguration> configs;
    private final Map<String, File> configFiles;
    
    // Config file names
    public static final String MAIN_CONFIG = "config.yml";
    public static final String QUESTS_CONFIG = "quests.yml";
    public static final String NPCS_CONFIG = "npcs.yml";
    public static final String REWARDS_CONFIG = "rewards.yml";
    public static final String MESSAGES_CONFIG = "messages.yml";
    
    public ConfigManager(MysticQuest plugin) {
        this.plugin = plugin;
        this.configs = new HashMap<>();
        this.configFiles = new HashMap<>();
    }
    
    public void loadConfigs() {
        // Initialize all config files
        initConfig(MAIN_CONFIG);
        initConfig(QUESTS_CONFIG);
        initConfig(NPCS_CONFIG);
        initConfig(REWARDS_CONFIG);
        initConfig(MESSAGES_CONFIG);
        
        // Set default values if they don't exist
        setDefaultMainConfig();
        setDefaultQuestsConfig();
        setDefaultNPCsConfig();
        setDefaultRewardsConfig();
        setDefaultMessagesConfig();
    }
    
    private void initConfig(String configName) {
        File configFile = new File(plugin.getDataFolder(), configName);
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource(configName, false);
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        configs.put(configName, config);
        configFiles.put(configName, configFile);
    }
    
    public FileConfiguration getConfig(String configName) {
        return configs.getOrDefault(configName, null);
    }
    
    public void saveConfig(String configName) {
        File configFile = configFiles.get(configName);
        if (configFile == null) return;
        
        try {
            FileConfiguration config = configs.get(configName);
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save config to " + configFile);
            e.printStackTrace();
        }
    }
    
    public void reloadConfig(String configName) {
        File configFile = configFiles.get(configName);
        if (configFile == null) return;
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        configs.put(configName, config);
    }
    
    public void reloadAllConfigs() {
        for (String configName : configs.keySet()) {
            reloadConfig(configName);
        }
    }
    
    private void setDefaultMainConfig() {
        FileConfiguration config = getConfig(MAIN_CONFIG);
        if (config.getKeys(false).isEmpty()) {
            // NPC settings
            config.set("npc.spawn-interval", 300); // in seconds
            config.set("npc.despawn-time", 60); // in seconds
            config.set("npc.max-distance", 30); // max distance from player to spawn NPC
            config.set("npc.approach-distance", 5); // distance at which NPC approaches player
            config.set("npc.notification.enabled", true);
            config.set("npc.notification.sound", "ENTITY_VILLAGER_AMBIENT");
            config.set("npc.notification.particles", true);
            
            // Quest settings
            config.set("quest.max-active-quests", 3);
            config.set("quest.quest-item.material", "PAPER");
            config.set("quest.quest-item.glow", true);
            
            // XP settings
            config.set("xp.enabled", true);
            config.set("xp.storage-type", "FILE"); // FILE or DATABASE
            
            // Debug settings
            config.set("debug", false);
            
            saveConfig(MAIN_CONFIG);
        }
    }
    
    private void setDefaultQuestsConfig() {
        FileConfiguration config = getConfig(QUESTS_CONFIG);
        if (config.getKeys(false).isEmpty()) {
            // Kill quest example
            config.set("kill_zombie.type", "KILL");
            config.set("kill_zombie.name", "Zombie Hunter");
            config.set("kill_zombie.description", "Hunt down 10 zombies");
            config.set("kill_zombie.entity", "ZOMBIE");
            config.set("kill_zombie.amount", 10);
            config.set("kill_zombie.xp", 100);
            config.set("kill_zombie.time-limit", 1800); // in seconds, optional
            
            // Collection quest example
            config.set("collect_diamonds.type", "COLLECT");
            config.set("collect_diamonds.name", "Diamond Collector");
            config.set("collect_diamonds.description", "Collect 5 diamonds");
            config.set("collect_diamonds.item", "DIAMOND");
            config.set("collect_diamonds.amount", 5);
            config.set("collect_diamonds.xp", 200);
            
            // Exploration quest example
            config.set("explore_desert.type", "EXPLORE");
            config.set("explore_desert.name", "Desert Explorer");
            config.set("explore_desert.description", "Find the hidden desert temple");
            config.set("explore_desert.biome", "DESERT");
            config.set("explore_desert.structure", "DESERT_PYRAMID");
            config.set("explore_desert.xp", 300);
            
            // Craft quest example
            config.set("craft_anvil.type", "CRAFT");
            config.set("craft_anvil.name", "Smithing Time");
            config.set("craft_anvil.description", "Craft an anvil");
            config.set("craft_anvil.item", "ANVIL");
            config.set("craft_anvil.amount", 1);
            config.set("craft_anvil.xp", 200);
            
            // Interact quest example
            config.set("tame_wolf.type", "INTERACT");
            config.set("tame_wolf.name", "Dog Whisperer");
            config.set("tame_wolf.description", "Tame a wolf");
            config.set("tame_wolf.entity", "WOLF");
            config.set("tame_wolf.interaction", "TAME");
            config.set("tame_wolf.amount", 1);
            config.set("tame_wolf.xp", 200);
            
            saveConfig(QUESTS_CONFIG);
        }
    }
    
    private void setDefaultNPCsConfig() {
        FileConfiguration config = getConfig(NPCS_CONFIG);
        if (config.getKeys(false).isEmpty()) {
            // NPC Types
            config.set("mysterious_stranger.name", "Mysterious Stranger");
            config.set("mysterious_stranger.skin", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWU3MjZkOWJhYjUzZTM0NzEyZWVmOTk4MzEwMzQ5YWE2YzUxMDNlYTYxZWVjZGQ1MTFiNDNkMTYyNzRjNiJ9fX0=");
            config.set("mysterious_stranger.quest-types", new String[]{"kill_zombie", "collect_diamonds"});
            config.set("mysterious_stranger.story", "I come from the shadows with a task that requires your unique skills. This quest is for your eyes only. Will you accept this challenge?");
            
            config.set("explorer.name", "Explorer");
            config.set("explorer.skin", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGNhYzk3NzRkYTEyMTcyNDg1MzJjZTE0N2Y3ODMxZjY3YTEyZmRjY2ExY2YwY2I0YjM4NDhkZTZiYzY5In19fQ==");
            config.set("explorer.quest-types", new String[]{"explore_desert"});
            config.set("explorer.story", "I've heard whispers of a hidden treasure, but I need help to locate it. Only the bravest adventurers are fit for this journey.");
            
            saveConfig(NPCS_CONFIG);
        }
    }
    
    private void setDefaultRewardsConfig() {
        FileConfiguration config = getConfig(REWARDS_CONFIG);
        if (config.getKeys(false).isEmpty()) {
            // XP Rewards
            config.set("rewards.tier1.xp-required", 500);
            config.set("rewards.tier1.commands", new String[]{
                "give %player% diamond 5",
                "eco give %player% 1000"
            });
            
            config.set("rewards.tier2.xp-required", 1500);
            config.set("rewards.tier2.commands", new String[]{
                "give %player% netherite_ingot 2",
                "eco give %player% 3000"
            });
            
            config.set("rewards.tier3.xp-required", 5000);
            config.set("rewards.tier3.commands", new String[]{
                "give %player% enchanted_golden_apple 3",
                "eco give %player% 10000",
                "lp user %player% permission set mysticquest.specialitem true"
            });
            
            saveConfig(REWARDS_CONFIG);
        }
    }
    
    private void setDefaultMessagesConfig() {
        FileConfiguration config = getConfig(MESSAGES_CONFIG);
        if (config.getKeys(false).isEmpty()) {
            config.set("prefix", "&8[&6MysticQuest&8] &r");
            
            // NPC Messages
            config.set("npc.quest-offer", "&7*A mysterious figure approaches you and whispers*\n&eI have a secret quest for you. Take this scroll and tell no one.");
            config.set("npc.quest-choice", "&eType &a/accept &eor &c/reject &ein chat to respond.");
            config.set("npc.quest-accepted", "&7*The figure nods approvingly*\n&eYou have chosen wisely. This quest shall bring you great rewards.");
            config.set("npc.quest-declined", "&7*The figure looks disappointed and backs away into the shadows*\n&cPerhaps another time, when you are ready...");
            
            // Quest Messages
            config.set("quest.received", "&6You have received a secret quest scroll!");
            config.set("quest.completed", "&aYou have completed the quest: &e%quest_name%&a!");
            config.set("quest.failed", "&cYou have failed the quest: &e%quest_name%&c.");
            config.set("quest.progress", "&7Quest progress: &e%progress%/%total%");
            
            // XP Messages
            config.set("xp.gained", "&6+%xp% Mystic XP!");
            config.set("xp.total", "&6You now have &e%xp%&6 Mystic XP!");
            config.set("xp.reward-unlocked", "&6You have unlocked a new reward tier: &e%tier%&6!");
            
            saveConfig(MESSAGES_CONFIG);
        }
    }
} 