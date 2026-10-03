package me.theus.donutLifeSteal;

import me.theus.donutLifeSteal.commands.LifeStealCommand;
import me.theus.donutLifeSteal.database.Database;
import me.theus.donutLifeSteal.listeners.LifeStealListener;
import me.theus.donutLifeSteal.managers.LifeStealManager;
import me.theus.donutLifeSteal.utils.LifeStealExpansion;
import me.theus.donutLifeSteal.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class DonutLifeSteal extends JavaPlugin {

    private static DonutLifeSteal instance;
    private FileConfiguration messagesConfig;
    private FileConfiguration databaseConfig;

    private Database database;
    private LifeStealManager manager;

    @Override
    public void onEnable() {
        instance = this;

        loadConfigs();

        database = new Database(this);
        if (!database.connect()) {
            getLogger().severe("Não foi possível conectar ao banco de dados! Desativando o DonutLifeSteal...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        manager = new LifeStealManager(this);

        getServer().getPluginManager().registerEvents(new LifeStealListener(this), this);

        LifeStealCommand cmd = new LifeStealCommand(this);
        PluginCommand pc = getCommand("lifesteal");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new LifeStealExpansion(this).register();
            getLogger().info("PlaceholderAPI conectado com sucesso!");
        }

        getLogger().info("DonutLifeSteal v" + getDescription().getVersion() + " ativado com sucesso!");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.saveAllUsers();
        }
        if (database != null) {
            database.disconnect();
        }
        getLogger().info("DonutLifeSteal desativado com sucesso!");
    }

    public void loadConfigs() {
        saveDefaultConfig();
        reloadConfig();

        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        File databaseFile = new File(getDataFolder(), "database.yml");
        if (!databaseFile.exists()) {
            saveResource("database.yml", false);
        }
        databaseConfig = YamlConfiguration.loadConfiguration(databaseFile);
    }

    public void reloadPlugin() {
        loadConfigs();
        if (database != null) {
            database.disconnect();
        }
        database = new Database(this);
        database.connect();
    }

    public String getMessage(String path, String def) {
        if (messagesConfig == null) return Utils.color(def);
        return Utils.color(messagesConfig.getString(path, def));
    }

    public static DonutLifeSteal getInstance() {
        return instance;
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public FileConfiguration getDatabaseConfig() {
        return databaseConfig;
    }

    public Database getDatabase() {
        return database;
    }

    public LifeStealManager getManager() {
        return manager;
    }
}
