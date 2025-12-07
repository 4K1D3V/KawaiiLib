package dev.oumaimaa.kawaiilib;

import dev.oumaimaa.kawaiilib.annotations.*;
import dev.oumaimaa.kawaiilib.managers.command.CommandManager;
import dev.oumaimaa.kawaiilib.managers.config.ConfigManager;
import dev.oumaimaa.kawaiilib.managers.database.DatabaseManager;
import dev.oumaimaa.kawaiilib.managers.discord.DiscordManager;
import dev.oumaimaa.kawaiilib.managers.event.EventManager;
import dev.oumaimaa.kawaiilib.managers.gui.MenuManager;
import dev.oumaimaa.kawaiilib.managers.lang.LanguageManager;
import dev.oumaimaa.kawaiilib.managers.scheduler.TaskManager;
import dev.oumaimaa.kawaiilib.managers.update.UpdateChecker;
import dev.oumaimaa.kawaiilib.utils.CooldownManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.util.Set;
import java.util.logging.Level;

public final class Bootstrap extends JavaPlugin {

    private static Bootstrap instance;
    private Class<?> mainClass;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private CommandManager commandManager;
    private EventManager eventManager;
    private MenuManager menuManager;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private DiscordManager discordManager;
    private LanguageManager languageManager;
    private TaskManager taskManager;
    private UpdateChecker updateChecker;
    private CooldownManager cooldownManager;
    private Metrics metrics;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("Initializing KawaiiLib...");

        try {
            // Scan for @KawaiiPlugin
            Reflections reflections = scanForAnnotations();
            Set<Class<?>> pluginClasses = reflections.getTypesAnnotatedWith(KawaiiPlugin.class);

            if (pluginClasses.isEmpty()) {
                getLogger().severe("No @KawaiiPlugin found! Disabling...");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }

            mainClass = pluginClasses.iterator().next();
            KawaiiPlugin pluginInfo = mainClass.getAnnotation(KawaiiPlugin.class);
            getLogger().info("Found plugin: " + pluginInfo.name() + " v" + pluginInfo.version());

            // Initialize managers
            initializeManagers(reflections);

            getLogger().info("KawaiiLib enabled successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize KawaiiLib", e);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private @NotNull Reflections scanForAnnotations() {
        return new Reflections(new ConfigurationBuilder()
                .forPackages("")
                .addScanners(Scanners.TypesAnnotated, Scanners.MethodsAnnotated, Scanners.FieldsAnnotated));
    }

    private void initializeManagers(@NotNull Reflections reflections) {
        // Order matters for dependencies
        cooldownManager = new CooldownManager();
        configManager = new ConfigManager(this);
        languageManager = new LanguageManager(this);

        // Initialize database if configured
        Set<Class<?>> dbClasses = reflections.getTypesAnnotatedWith(Database.class);
        if (!dbClasses.isEmpty()) {
            databaseManager = new DatabaseManager(this, dbClasses.iterator().next().getAnnotation(Database.class));
        }

        // Initialize Discord if configured
        Set<Class<?>> discordClasses = reflections.getTypesAnnotatedWith(DiscordBot.class);
        if (!discordClasses.isEmpty()) {
            discordManager = new DiscordManager(this, discordClasses.iterator().next().getAnnotation(DiscordBot.class));
        }

        // Initialize core managers
        commandManager = new CommandManager(this, mainClass);
        eventManager = new EventManager(this, mainClass);
        menuManager = new MenuManager(this);
        taskManager = new TaskManager(this);

        // Process all annotations
        commandManager.registerCommands(reflections);
        eventManager.registerEvents(reflections);
        menuManager.registerMenus(reflections);
        configManager.loadConfigs(reflections);
        languageManager.loadLanguages(reflections);
        taskManager.scheduleTasks(reflections);

        // Initialize metrics if configured
        Metrics metricsAnn = mainClass.getAnnotation(Metrics.class);
        if (metricsAnn != null) {
            metrics = new Metrics(this, metricsAnn.id());
            getLogger().info("Metrics enabled with ID: " + metricsAnn.id());
        }

        // Check for updates if configured
        AutoUpdate autoUpdate = mainClass.getAnnotation(AutoUpdate.class);
        if (autoUpdate != null) {
            updateChecker = new UpdateChecker(this, autoUpdate);
            updateChecker.checkForUpdates().thenAccept(hasUpdate -> {
                if (hasUpdate) {
                    getLogger().info("A new update is available!");
                }
            });
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down KawaiiLib...");

        // Shutdown managers in reverse order
        if (taskManager != null) {
            taskManager.shutdown();
        }

        if (discordManager != null) {
            discordManager.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("KawaiiLib disabled successfully!");
    }

    public static Bootstrap getInstance() {
        return instance;
    }

    public Class<?> getMainClass() {
        return mainClass;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public DiscordManager getDiscordManager() {
        return discordManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    public @NotNull Component formatText(String text) {
        return miniMessage.deserialize(text);
    }
}