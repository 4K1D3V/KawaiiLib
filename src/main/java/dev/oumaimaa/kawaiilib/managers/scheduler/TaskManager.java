package dev.oumaimaa.kawaiilib.managers.scheduler;

import dev.oumaimaa.kawaiilib.Bootstrap;
import dev.oumaimaa.kawaiilib.annotations.Task;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class TaskManager {

    private final Bootstrap plugin;
    private final List<BukkitTask> activeTasks = new ArrayList<>();
    private Object mainInstance;

    public TaskManager(Bootstrap plugin) {
        this.plugin = plugin;
        try {
            this.mainInstance = plugin.getMainClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to instantiate main class: " + e.getMessage());
        }
    }

    public void scheduleTasks(@NotNull Reflections reflections) {
        Set<Method> taskMethods = reflections.getMethodsAnnotatedWith(Task.class);

        for (Method method : taskMethods) {
            Task ann = method.getAnnotation(Task.class);

            Runnable task = () -> {
                try {
                    method.setAccessible(true);
                    method.invoke(mainInstance);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error executing task " + method.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            };

            BukkitTask scheduledTask = scheduleTask(task, ann);
            if (scheduledTask != null) {
                activeTasks.add(scheduledTask);
                plugin.getLogger().info("Scheduled task: " + method.getName() +
                        (ann.async() ? " (async)" : " (sync)"));
            }
        }
    }

    private BukkitTask scheduleTask(@NotNull Runnable task, @NotNull Task config) {
        if (config.period() > 0) {
            // Repeating task
            if (config.async()) {
                return Bukkit.getScheduler().runTaskTimerAsynchronously(
                        plugin, task, config.delay(), config.period()
                );
            } else {
                return Bukkit.getScheduler().runTaskTimer(
                        plugin, task, config.delay(), config.period()
                );
            }
        } else {
            // One-time delayed task
            if (config.async()) {
                return Bukkit.getScheduler().runTaskLaterAsynchronously(
                        plugin, task, config.delay()
                );
            } else {
                return Bukkit.getScheduler().runTaskLater(
                        plugin, task, config.delay()
                );
            }
        }
    }

    public void shutdown() {
        activeTasks.forEach(BukkitTask::cancel);
        activeTasks.clear();
        plugin.getLogger().info("All scheduled tasks cancelled");
    }
}