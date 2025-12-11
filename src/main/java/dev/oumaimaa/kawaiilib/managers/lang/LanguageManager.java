package dev.oumaimaa.kawaiilib.managers.lang;

import dev.oumaimaa.kawaiilib.Bootstrap;
import dev.oumaimaa.kawaiilib.annotations.Lang;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LanguageManager {

    private final Bootstrap plugin;
    private final Map<String, YamlConfiguration> languages = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerLanguages = new ConcurrentHashMap<>();
    private String defaultLanguage = "en";

    public LanguageManager(Bootstrap plugin) {
        this.plugin = plugin;
    }

    public void loadLanguages(@NotNull Reflections reflections) {
        Set<Class<?>> langClasses = reflections.getTypesAnnotatedWith(Lang.class);

        if (langClasses.isEmpty()) {
            return; // No language support configured
        }

        Lang ann = langClasses.iterator().next().getAnnotation(Lang.class);
        File langFolder = new File(plugin.getDataFolder(), ann.folder());

        if (!langFolder.exists()) {
            langFolder.mkdirs();
            createDefaultLanguageFile(langFolder);
        }

        File[] files = langFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String langCode = file.getName().replace(".yml", "");
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                languages.put(langCode, yaml);
                plugin.getLogger().info("Loaded language: " + langCode);
            }
        }

        if (!languages.containsKey("en")) {
            plugin.getLogger().warning("No English language file found, using defaults");
        }
    }

    private void createDefaultLanguageFile(@NotNull File langFolder) {
        File enFile = new File(langFolder, "en.yml");
        if (!enFile.exists()) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("prefix", "<gradient:#00ff00:#00aa00>[KawaiiLib]</gradient>");
            yaml.set("no-permission", "<red>You don't have permission to do that!");
            yaml.set("player-not-found", "<red>Player not found!");
            yaml.set("reload-success", "<green>Configuration reloaded successfully!");

            try {
                yaml.save(enFile);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to create default language file");
                e.printStackTrace();
            }
        }
    }

    public String getMessage(@NotNull String key, String language) {
        YamlConfiguration yaml = languages.getOrDefault(language, languages.get(defaultLanguage));
        if (yaml == null) {
            return key;
        }

        return yaml.getString(key, key);
    }

    public @NotNull Component getComponent(@NotNull String key, String language, Object @NotNull ... replacements) {
        String message = getMessage(key, language);

        // Apply replacements
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                String placeholder = replacements[i].toString();
                String value = replacements[i + 1].toString();
                message = message.replace(placeholder, value);
            }
        }

        return plugin.formatText(message);
    }

    public @NotNull Component getPlayerMessage(@NotNull Player player, @NotNull String key, Object... replacements) {
        String language = playerLanguages.getOrDefault(player.getUniqueId(), defaultLanguage);
        return getComponent(key, language, replacements);
    }

    public void setPlayerLanguage(@NotNull Player player, String language) {
        if (languages.containsKey(language)) {
            playerLanguages.put(player.getUniqueId(), language);
        }
    }

    public String getPlayerLanguage(@NotNull Player player) {
        return playerLanguages.getOrDefault(player.getUniqueId(), defaultLanguage);
    }

    @Contract(pure = true)
    public @NotNull Set<String> getAvailableLanguages() {
        return languages.keySet();
    }

    public void setDefaultLanguage(String language) {
        if (languages.containsKey(language)) {
            this.defaultLanguage = language;
        }
    }
}