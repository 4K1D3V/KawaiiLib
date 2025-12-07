package dev.oumaimaa.kawaiilib;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.oumaimaa.kawaiilib.annotations.*;
import dev.oumaimaa.kawaiilib.annotations.EventListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;

public class Bootstrap extends JavaPlugin implements Listener {

    private static Bootstrap instance;
    private Class<?> mainClass;
    private final Map<String, Method> commandMethods = new HashMap<>();
    private final Map<String, Method> tabCompleters = new HashMap<>();
    private final Map<Class<?>, Object> menuInstances = new HashMap<>();
    private final Map<String, YamlConfiguration> configs = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private JDA jda;
    private HikariDataSource dataSource;
    private Metrics metrics;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public void onEnable() {
        instance = this;
        // Scan for @KawaiiPlugin
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .forPackages("") // Scan all loaded classes
                .addScanners(Scanners.TypesAnnotated, Scanners.MethodsAnnotated));
        Set<Class<?>> pluginClasses = reflections.getTypesAnnotatedWith(KawaiiPlugin.class);
        if (pluginClasses.isEmpty()) {
            getLogger().severe("No @KawaiiPlugin found!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        mainClass = pluginClasses.iterator().next();
        processAnnotations(reflections);
        Bukkit.getPluginManager().registerEvents(this, this);
        loadYamlMode();
    }

    private void processAnnotations(@NotNull Reflections reflections) {
        // Commands
        Set<Method> commandMethodsSet = reflections.getMethodsAnnotatedWith(Command.class);
        for (Method method : commandMethodsSet) {
            Command ann = method.getAnnotation(Command.class);
            PluginCommand cmd = getCommand(ann.name());
            if (cmd == null) {
                cmd = new PluginCommand(ann.name(), this);
                getServer().getCommandMap().register(getDescription().getName(), cmd);
            }
            cmd.setExecutor((sender, command, label, args) -> {
                try {
                    method.invoke(mainClass.getDeclaredConstructor().newInstance(), sender, args);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;
            });
            commandMethods.put(ann.name(), method);
        }

        // TabCompleters
        Set<Method> tabMethods = reflections.getMethodsAnnotatedWith(TabCompleter.class);
        for (Method method : tabMethods) {
            TabCompleter ann = method.getAnnotation(TabCompleter.class);
            tabCompleters.put(ann.forCommand(), method);
            PluginCommand cmd = getCommand(ann.forCommand());
            if (cmd != null) {
                cmd.setTabCompleter((sender, command, alias, args) -> {
                    try {
                        return (List<String>) method.invoke(mainClass.getDeclaredConstructor().newInstance(), args);
                    } catch (Exception e) {
                        return Collections.emptyList();
                    }
                });
            }
        }

        // Menus
        Set<Class<?>> menuClasses = reflections.getTypesAnnotatedWith(Menu.class);
        for (Class<?> clazz : menuClasses) {
            try {
                Object instance = clazz.getDeclaredConstructor().newInstance();
                menuInstances.put(clazz, instance);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // EventListeners
        Set<Method> eventMethods = reflections.getMethodsAnnotatedWith(EventListener.class);
        for (Method method : eventMethods) {
            Class<?> eventType = method.getParameterTypes()[0];
            Bukkit.getPluginManager().registerEvent((Class) eventType, this, method.getAnnotation(EventListener.class).priority(), (listener, event) -> {
                try {
                    method.invoke(mainClass.getDeclaredConstructor().newInstance(), event);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, this, method.getAnnotation(EventListener.class).ignoreCancelled());
        }

        // Configs
        Set<Class<?>> configClasses = reflections.getTypesAnnotatedWith(Config.class);
        for (Class<?> clazz : configClasses) {
            Config ann = clazz.getAnnotation(Config.class);
            File file = new File(getDataFolder(), ann.file());
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            configs.put(ann.file(), config);
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    Object value = config.get(field.getName());
                    if (value != null) {
                        field.set(null, value);
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        // Visuals and other method annotations
        // For @ActionBar, @Title, etc., assume they are on methods that are called when needed, use reflection to invoke when triggered (e.g., from events or commands)
        // For simplicity, assume user invokes them manually, or tie to events.

        // Discord
        Set<Class<?>> discordClasses = reflections.getTypesAnnotatedWith(DiscordBot.class);
        if (!discordClasses.isEmpty()) {
            DiscordBot ann = discordClasses.iterator().next().getAnnotation(DiscordBot.class);
            try {
                jda = JDABuilder.createLight(ann.token()).build();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // AutoUpdate
        AutoUpdate autoUpdate = mainClass.getAnnotation(AutoUpdate.class);
        if (autoUpdate != null) {
            checkForUpdate(autoUpdate.platform(), autoUpdate.resourceId(), autoUpdate.autoDownload());
        }

        // Database
        Set<Class<?>> dbClasses = reflections.getTypesAnnotatedWith(Database.class);
        if (!dbClasses.isEmpty()) {
            Database ann = dbClasses.iterator().next().getAnnotation(Database.class);
            HikariConfig hikariConfig = new HikariConfig();
            if (ann.type().equals("MYSQL")) {
                hikariConfig.setJdbcUrl(ann.url());
                hikariConfig.setUsername(ann.user());
                hikariConfig.setPassword(ann.password());
            } else if (ann.type().equals("SQLITE")) {
                hikariConfig.setJdbcUrl("jdbc:sqlite:" + getDataFolder() + "/data.db");
            } // Handle H2 similarly
            dataSource = new HikariDataSource(hikariConfig);
        }

        // Metrics
        Metrics metricsAnn = mainClass.getAnnotation(Metrics.class);
        if (metricsAnn != null) {
            metrics = new Metrics(this, metricsAnn.id());
        }

        // Other annotations like @Cooldown are checked at runtime when methods are invoked
    }

    private void loadYamlMode() {
        // Load from kawaii/ folder and simulate annotations
        // For example, for commands.yml, parse and create dynamic executors
        File yamlDir = new File(getDataFolder(), "kawaii");
        if (yamlDir.exists()) {
            // Implement parsing logic here, e.g., for commands.yml
            // YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(yamlDir, "commands.yml"));
            // For each section, create PluginCommand dynamically
        }
    }

    private void checkForUpdate(String platform, String resourceId, boolean autoDownload) {
        Executors.newVirtualThreadPerTaskExecutor().execute(() -> {
            try {
                String latestVersion = switch (platform) {
                    case "SPIGOT" -> {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("https://api.spigotmc.org/simple/0.2/index.php?action=getResource&id=" + resourceId))
                                .build();
                        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                        // Parse JSON or HTML for version, assume parsing logic
                        yield "parsed_version"; // Placeholder
                    }
                    case "HANGAR" -> {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("https://hangar.papermc.io/api/v1/projects/" + resourceId + "/latest"))
                                .build();
                        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                        yield "parsed_version";
                    }
                    case "MODRINTH" -> {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("https://api.modrinth.com/v2/project/" + resourceId + "/version"))
                                .build();
                        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                        // Get latest from JSON array
                        yield "parsed_version";
                    }
                    default -> null;
                };

                String currentVersion = getDescription().getVersion();
                if (latestVersion != null && !latestVersion.equals(currentVersion)) {
                    getLogger().info("New version available: " + latestVersion);
                    if (autoDownload) {
                        // Download logic, e.g., for Modrinth: get download URL from API
                        HttpRequest downloadRequest = HttpRequest.newBuilder()
                                .uri(URI.create("download_url"))
                                .build();
                        HttpResponse<InputStream> downloadResponse = httpClient.send(downloadRequest, HttpResponse.BodyHandlers.ofInputStream());
                        Files.copy(downloadResponse.body(), new File("plugins/update.jar").toPath(), StandardCopyOption.REPLACE_EXISTING);
                        getLogger().info("Downloaded update. Restart to apply.");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // Example for cooldown check
    public boolean checkCooldown(UUID uuid, long seconds) {
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(uuid, 0L);
        if (now - last < seconds * 1000) {
            return false;
        }
        cooldowns.put(uuid, now);
        return true;
    }

    // Text formatting
    public Component formatText(String text) {
        return miniMessage.deserialize(text);
    }

    // Player head
    public ItemStack getSkull(String owner) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
        skull.setItemMeta(meta);
        return skull;
    }

    // Database connection
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // Etc. for other features

    @Override
    public void onDisable() {
        if (jda != null) jda.shutdown();
        if (dataSource != null) dataSource.close();
    }
}