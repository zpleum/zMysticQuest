package me.zpleum.zmystic.quest;

import me.zpleum.zmystic.quest.commands.AdminCommands;
import me.zpleum.zmystic.quest.commands.MysticQuestCommand;
import me.zpleum.zmystic.quest.commands.QuestResponseCommand;
import me.zpleum.zmystic.quest.config.ConfigManager;
import me.zpleum.zmystic.quest.listeners.PlayerListener;
import me.zpleum.zmystic.quest.managers.NPCManager;
import me.zpleum.zmystic.quest.managers.QuestManager;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MysticQuest extends JavaPlugin {

    private static MysticQuest instance;
    private ConfigManager configManager;
    private NPCManager npcManager;
    private QuestManager questManager;

    private String getVersionFromWeb() {
        String latestVersion = "Unknown";
        try {
            URL url = new URL("https://zpleum.is-a.dev/api/get-version/zmysticquest");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            JSONObject jsonResponse = new JSONObject(response.toString());
            latestVersion = jsonResponse.getString("version");

        } catch (Exception e) {
            e.printStackTrace();
        }
        return latestVersion;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Ensure the plugin's data folder exists
        createDataFolder();

        // Check for Citizens plugin
        if (!isCitizensPluginAvailable()) return;

        // Initialize configurations and managers
        initializeComponents();

        // Register event listeners and commands
        registerListenersAndCommands();

        // Start NPC spawning task
        npcManager.startSpawningTask();

        String currentVersion = "2.0";
        String latestVersion = getVersionFromWeb();

        getLogger().info("zMysticQuest has been successfully enabled!");
        getLogger().info("Current version " + currentVersion + " Latest " + latestVersion);
    }

    private void createDataFolder() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
    }

    private boolean isCitizensPluginAvailable() {
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null) {
            getLogger().severe("Citizens plugin not found! Disabling MysticQuest...");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }

        try {
            CitizensAPI.getNPCRegistry();
            getLogger().info("Successfully connected to Citizens API.");
            return true;
        } catch (Exception e) {
            getLogger().severe("Failed to connect to Citizens API: " + e.getMessage());
            getLogger().severe("Please ensure you are using Citizens 2.0.32 or higher.");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    private void initializeComponents() {
        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        questManager = new QuestManager(this);
        npcManager = new NPCManager(this);
    }

    private void registerListenersAndCommands() {
        // Register event listeners
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

        // Register main command
        getCommand("mysticquest").setExecutor(new MysticQuestCommand(this));

        // Register quest response commands
        registerResponseCommands();

        // Register admin commands
        registerAdminCommands();
    }

    private void registerResponseCommands() {
        // Create and register response commands
        QuestResponseCommand acceptCommand = new QuestResponseCommand(this, true);
        QuestResponseCommand rejectCommand = new QuestResponseCommand(this, false);
        QuestResponseCommand unstuckCommand = new QuestResponseCommand(this, false, true);

        registerCommand("accept", acceptCommand);
        registerCommand("reject", rejectCommand);
        registerCommand("unstuck", unstuckCommand);
    }

    private void registerCommand(String commandName, QuestResponseCommand commandExecutor) {
        PluginCommand command = getCommand(commandName);
        if (command != null) {
            command.setExecutor(commandExecutor);
        } else {
            getLogger().warning("Failed to register /" + commandName + " command!");
        }
    }

    private void registerAdminCommands() {
        PluginCommand mqAdminCmd = getCommand("mqadmin");
        if (mqAdminCmd != null) {
            AdminCommands adminCommands = new AdminCommands(this);
            mqAdminCmd.setExecutor(adminCommands);
            mqAdminCmd.setTabCompleter(adminCommands);
        } else {
            getLogger().warning("Failed to register /mqadmin command!");
        }
    }

    @Override
    public void onDisable() {
        // Clean up tasks and save data on disable
        if (npcManager != null) npcManager.stopSpawningTask();
        if (questManager != null) questManager.savePlayerData();

        String currentVersion = "2.0";
        String latestVersion = getVersionFromWeb();

        getLogger().info("zMysticQuest has been successfully disabled!");
        getLogger().info("Current version " + currentVersion + " Latest " + latestVersion);
    }

    public static MysticQuest getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public NPCManager getNPCManager() {
        return npcManager;
    }

    public QuestManager getQuestManager() {
        return questManager;
    }
}
