package dev.oumaimaa.kawaiilib.managers.command;

import dev.oumaimaa.kawaiilib.Bootstrap;
import dev.oumaimaa.kawaiilib.annotations.Command;
import dev.oumaimaa.kawaiilib.annotations.Cooldown;
import dev.oumaimaa.kawaiilib.annotations.Subcommand;
import dev.oumaimaa.kawaiilib.annotations.TabCompleter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class CommandManager {

    private final Bootstrap plugin;
    private final Class<?> mainClass;
    private final Map<String, CommandWrapper> commands = new ConcurrentHashMap<>();
    private final Map<String, List<SubcommandWrapper>> subcommands = new ConcurrentHashMap<>();
    private final Map<String, Method> tabCompleters = new ConcurrentHashMap<>();
    private Object mainInstance;

    public CommandManager(Bootstrap plugin, Class<?> mainClass) {
        this.plugin = plugin;
        this.mainClass = mainClass;
        try {
            this.mainInstance = mainClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to instantiate main class: " + e.getMessage());
        }
    }

    public void registerCommands(@NotNull Reflections reflections) {
        Set<Method> commandMethods = reflections.getMethodsAnnotatedWith(Command.class);

        for (Method method : commandMethods) {
            Command ann = method.getAnnotation(Command.class);
            Cooldown cooldown = method.getAnnotation(Cooldown.class);

            CommandWrapper wrapper = new CommandWrapper(method, ann, cooldown, mainInstance);
            commands.put(ann.name(), wrapper);

            PluginCommand cmd = plugin.getServer().getPluginCommand(ann.name());
            if (cmd == null) {
                cmd = plugin.getCommand(ann.name());
                if (cmd == null) {
                    plugin.getLogger().warning("Command not found in plugin.yml: " + ann.name());
                    continue;
                }
            }

            cmd.setExecutor(createExecutor(ann.name()));
            cmd.setTabCompleter(createTabCompleter(ann.name()));

            if (ann.aliases().length > 0) {
                cmd.setAliases(Arrays.asList(ann.aliases()));
            }

            if (!ann.permission().isEmpty()) {
                cmd.setPermission(ann.permission());
            }

            if (!ann.usage().isEmpty()) {
                cmd.setUsage(ann.usage());
            }

            plugin.getLogger().info("Registered command: /" + ann.name());
        }

        registerSubcommands(reflections);
        registerTabCompleters(reflections);
    }

    private void registerSubcommands(@NotNull Reflections reflections) {
        Set<Method> subcommandMethods = reflections.getMethodsAnnotatedWith(Subcommand.class);

        for (Method method : subcommandMethods) {
            Subcommand ann = method.getAnnotation(Subcommand.class);
            String[] parts = ann.name().split("\\.");

            if (parts.length < 2) continue;

            String parentCommand = parts[0];
            String subcommandName = parts[1];

            SubcommandWrapper wrapper = new SubcommandWrapper(method, subcommandName, mainInstance);
            subcommands.computeIfAbsent(parentCommand, k -> new ArrayList<>()).add(wrapper);
        }
    }

    private void registerTabCompleters(@NotNull Reflections reflections) {
        Set<Method> tabMethods = reflections.getMethodsAnnotatedWith(TabCompleter.class);

        for (Method method : tabMethods) {
            TabCompleter ann = method.getAnnotation(TabCompleter.class);
            tabCompleters.put(ann.forCommand(), method);
        }
    }

    private @NotNull CommandExecutor createExecutor(String commandName) {
        return (sender, command, label, args) -> {
            CommandWrapper wrapper = commands.get(commandName);
            if (wrapper == null) return false;

            // Check permission
            if (!wrapper.annotation.permission().isEmpty() &&
                    !sender.hasPermission(wrapper.annotation.permission())) {
                sender.sendMessage(plugin.formatText("<red>You don't have permission to use this command!"));
                return true;
            }

            // Check cooldown - FIXED: Pass cooldown duration and get proper remaining time
            if (sender instanceof Player player && wrapper.cooldown != null) {
                String cooldownKey = commandName;
                long cooldownSeconds = wrapper.cooldown.seconds();

                if (plugin.getCooldownManager().isOnCooldown(cooldownKey, player.getUniqueId(), Duration.ofSeconds(cooldownSeconds))) {
                    long remaining = plugin.getCooldownManager().getRemaining(cooldownKey, player.getUniqueId(), cooldownSeconds);
                    String message = wrapper.cooldown.message().replace("%time%", String.valueOf(remaining));
                    sender.sendMessage(plugin.formatText("<red>" + message));
                    return true;
                }
                plugin.getCooldownManager().setCooldown(cooldownKey, player.getUniqueId());
            }

            // Handle subcommands
            if (args.length > 0) {
                List<SubcommandWrapper> subs = subcommands.get(commandName);
                if (subs != null) {
                    Optional<SubcommandWrapper> subOpt = subs.stream()
                            .filter(s -> s.name.equalsIgnoreCase(args[0]))
                            .findFirst();

                    if (subOpt.isPresent()) {
                        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                        return executeMethod(subOpt.get().method, sender, subArgs);
                    }
                }
            }

            // Execute main command
            return executeMethod(wrapper.method, sender, args);
        };
    }

    private boolean executeMethod(Method method, CommandSender sender, String[] args) {
        try {
            Class<?>[] paramTypes = method.getParameterTypes();

            if (paramTypes.length == 2 &&
                    CommandSender.class.isAssignableFrom(paramTypes[0]) &&
                    paramTypes[1].isArray()) {
                method.invoke(mainInstance, sender, args);
            } else if (paramTypes.length == 1 && CommandSender.class.isAssignableFrom(paramTypes[0])) {
                method.invoke(mainInstance, sender);
            } else {
                method.invoke(mainInstance);
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error executing command: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Contract(pure = true)
    private org.bukkit.command.@NotNull TabCompleter createTabCompleter(String commandName) {
        return (sender, command, alias, args) -> {
            Method tabMethod = tabCompleters.get(commandName);

            if (tabMethod != null) {
                try {
                    return (List<String>) tabMethod.invoke(mainInstance, (Object[]) args);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error in tab completer: " + e.getMessage());
                }
            }

            // Default tab completion for subcommands
            if (args.length == 1) {
                List<SubcommandWrapper> subs = subcommands.get(commandName);
                if (subs != null) {
                    return subs.stream()
                            .map(s -> s.name)
                            .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }

            return Collections.emptyList();
        };
    }

    public Class<?> getMainClass() {
        return mainClass;
    }

    private record CommandWrapper(Method method, Command annotation, Cooldown cooldown, Object instance) {}

    private record SubcommandWrapper(Method method, String name, Object instance) {}
}