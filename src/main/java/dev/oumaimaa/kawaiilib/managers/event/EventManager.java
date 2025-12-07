package dev.oumaimaa.kawaiilib.managers.event;

import dev.oumaimaa.kawaiilib.Bootstrap;
import dev.oumaimaa.kawaiilib.annotations.EventListener;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;

import java.lang.reflect.Method;
import java.util.Set;

public final class EventManager implements Listener {

    private final Bootstrap plugin;
    private final Class<?> mainClass;
    private Object mainInstance;

    public EventManager(Bootstrap plugin, Class<?> mainClass) {
        this.plugin = plugin;
        this.mainClass = mainClass;
        try {
            this.mainInstance = mainClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to instantiate main class: " + e.getMessage());
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void registerEvents(@NotNull Reflections reflections) {
        Set<Method> eventMethods = reflections.getMethodsAnnotatedWith(EventListener.class);

        for (Method method : eventMethods) {
            EventListener ann = method.getAnnotation(EventListener.class);

            if (method.getParameterCount() != 1) {
                plugin.getLogger().warning("Event listener must have exactly one parameter: " + method.getName());
                continue;
            }

            Class<?> eventType = method.getParameterTypes()[0];
            if (!Event.class.isAssignableFrom(eventType)) {
                plugin.getLogger().warning("Event listener parameter must extend Event: " + method.getName());
                continue;
            }

            registerEventHandler(eventType.asSubclass(Event.class), method, ann);
            plugin.getLogger().info("Registered event listener: " + method.getName() + " for " + eventType.getSimpleName());
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Event> void registerEventHandler(
            @NotNull Class<T> eventClass,
            Method method,
            @NotNull EventListener annotation) {

        Bukkit.getPluginManager().registerEvent(
                eventClass,
                this,
                annotation.priority(),
                (listener, event) -> handleEvent(method, event),
                plugin,
                annotation.ignoreCancelled()
        );
    }

    private void handleEvent(Method method, Event event) {
        try {
            method.setAccessible(true);
            method.invoke(mainInstance, event);
        } catch (Exception e) {
            plugin.getLogger().severe("Error handling event in " + method.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}