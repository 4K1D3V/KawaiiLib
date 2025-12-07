package dev.oumaimaa.kawaiilib.managers.config;

import dev.oumaimaa.kawaiilib.Bootstrap;
import dev.oumaimaa.kawaiilib.annotations.Config;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reflections.Reflections;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfigManager {

    private final Bootstrap plugin;
    private final Map<String, ConfigWrapper> configs = new ConcurrentHashMap<>();

    public ConfigManager(Bootstrap plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs(@NotNull Reflections reflections) {
        Set<Class<?>> configClasses = reflections.getTypesAnnotatedWith(Config.class);

        for (Class<?> clazz : configClasses) {
            Config ann = clazz.getAnnotation(Config.class);

            File file = new File(plugin.getDataFolder(), ann.file());
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                plugin.saveResource(ann.file(), false);
            }

            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigWrapper wrapper = new ConfigWrapper(clazz, yaml, file, ann);
            configs.put(ann.file(), wrapper);

            loadFieldsFromConfig(clazz, yaml);

            plugin.getLogger().info("Loaded config: " + ann.file());
        }
    }

    private void loadFieldsFromConfig(@NotNull Class<?> clazz, @NotNull YamlConfiguration yaml) {
        for (Field field : clazz.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            field.setAccessible(true);
            String path = field.getName().replace("_", ".");

            try {
                Object value = yaml.get(path);
                if (value != null) {
                    // Type conversion
                    if (field.getType() == int.class || field.getType() == Integer.class) {
                        field.set(null, yaml.getInt(path));
                    } else if (field.getType() == double.class || field.getType() == Double.class) {
                        field.set(null, yaml.getDouble(path));
                    } else if (field.getType() == boolean.class || field.getType() == Boolean.class) {
                        field.set(null, yaml.getBoolean(path));
                    } else if (field.getType() == String.class) {
                        field.set(null, yaml.getString(path));
                    } else if (field.getType() == java.util.List.class) {
                        field.set(null, yaml.getList(path));
                    } else {
                        field.set(null, value);
                    }
                }
            } catch (IllegalAccessException e) {
                plugin.getLogger().warning("Failed to set config field: " + field.getName());
                e.printStackTrace();
            }
        }
    }

    public @Nullable YamlConfiguration getConfig(String fileName) {
        ConfigWrapper wrapper = configs.get(fileName);
        return wrapper != null ? wrapper.yaml : null;
    }

    public void saveConfig(String fileName) {
        ConfigWrapper wrapper = configs.get(fileName);
        if (wrapper != null) {
            try {
                wrapper.yaml.save(wrapper.file);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save config: " + fileName);
                e.printStackTrace();
            }
        }
    }

    public void reloadConfig(String fileName) {
        ConfigWrapper wrapper = configs.get(fileName);
        if (wrapper != null) {
            YamlConfiguration newYaml = YamlConfiguration.loadConfiguration(wrapper.file);
            // FIXED: Create new wrapper instead of modifying final field
            ConfigWrapper newWrapper = new ConfigWrapper(wrapper.clazz, newYaml, wrapper.file, wrapper.annotation);
            configs.put(fileName, newWrapper);
            loadFieldsFromConfig(wrapper.clazz, newYaml);
            plugin.getLogger().info("Reloaded config: " + fileName);
        }
    }

    public void saveAll() {
        configs.forEach((name, wrapper) -> {
            if (wrapper.annotation.autoSave()) {
                try {
                    wrapper.yaml.save(wrapper.file);
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to save config: " + name);
                    e.printStackTrace();
                }
            }
        });
    }

    // FIXED: Removed 'yaml' from record to make it mutable through wrapper replacement
    private static class ConfigWrapper {
        final Class<?> clazz;
        final YamlConfiguration yaml;
        final File file;
        final Config annotation;

        ConfigWrapper(Class<?> clazz, YamlConfiguration yaml, File file, Config annotation) {
            this.clazz = clazz;
            this.yaml = yaml;
            this.file = file;
            this.annotation = annotation;
        }
    }
}