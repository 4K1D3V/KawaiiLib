package dev.oumaimaa.kawaiilib.managers.gui;

import dev.oumaimaa.kawaiilib.Bootstrap;
import dev.oumaimaa.kawaiilib.annotations.Button;
import dev.oumaimaa.kawaiilib.annotations.Close;
import dev.oumaimaa.kawaiilib.annotations.Menu;
import dev.oumaimaa.kawaiilib.annotations.PaginatedMenu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuManager implements Listener {

    private final Bootstrap plugin;
    private final Map<Class<?>, MenuWrapper> menus = new ConcurrentHashMap<>();
    private final Map<UUID, MenuSession> activeSessions = new ConcurrentHashMap<>();

    public MenuManager(Bootstrap plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void registerMenus(@NotNull Reflections reflections) {
        Set<Class<?>> menuClasses = reflections.getTypesAnnotatedWith(Menu.class);

        for (Class<?> clazz : menuClasses) {
            try {
                Menu ann = clazz.getAnnotation(Menu.class);
                Object instance = clazz.getDeclaredConstructor().newInstance();

                Map<Integer, ButtonWrapper> buttons = new HashMap<>();
                Method closeMethod = null;

                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Button.class)) {
                        Button btnAnn = method.getAnnotation(Button.class);
                        buttons.put(btnAnn.slot(), new ButtonWrapper(method, btnAnn, instance));
                    }

                    if (method.isAnnotationPresent(Close.class)) {
                        closeMethod = method;
                    }
                }

                MenuWrapper wrapper = new MenuWrapper(instance, ann, buttons, closeMethod);
                menus.put(clazz, wrapper);

                plugin.getLogger().info("Registered menu: " + ann.title());
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to register menu: " + clazz.getName());
                e.printStackTrace();
            }
        }

        // Register paginated menus
        Set<Class<?>> paginatedMenuClasses = reflections.getTypesAnnotatedWith(PaginatedMenu.class);
        for (Class<?> clazz : paginatedMenuClasses) {
            try {
                PaginatedMenu ann = clazz.getAnnotation(PaginatedMenu.class);
                Object instance = clazz.getDeclaredConstructor().newInstance();

                plugin.getLogger().info("Registered paginated menu: " + ann.title());
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to register paginated menu: " + clazz.getName());
                e.printStackTrace();
            }
        }
    }

    public void openMenu(@NotNull Player player, @NotNull Class<?> menuClass) {
        MenuWrapper wrapper = menus.get(menuClass);
        if (wrapper == null) {
            plugin.getLogger().warning("Menu not found: " + menuClass.getName());
            return;
        }

        Inventory inv = createInventory(wrapper);
        populateInventory(inv, wrapper);

        MenuSession session = new MenuSession(menuClass, inv, wrapper);
        activeSessions.put(player.getUniqueId(), session);

        player.openInventory(inv);
    }

    private @NotNull Inventory createInventory(@NotNull MenuWrapper wrapper) {
        Menu ann = wrapper.annotation;
        int size = ann.rows() * 9;

        return Bukkit.createInventory(
                null,
                size,
                plugin.formatText(ann.title())
        );
    }

    private void populateInventory(@NotNull Inventory inv, @NotNull MenuWrapper wrapper) {
        for (Map.Entry<Integer, ButtonWrapper> entry : wrapper.buttons.entrySet()) {
            int slot = entry.getKey();
            ButtonWrapper button = entry.getValue();

            ItemStack item = parseItemString(button.annotation.item());
            inv.setItem(slot, item);
        }
    }

    private @NotNull ItemStack parseItemString(@NotNull String itemString) {
        // Parse format: "MATERIAL{name='Name',lore=['Line1','Line2']}"
        String[] parts = itemString.split("\\{", 2);
        String materialName = parts[0].trim();

        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.STONE;
        }

        ItemStack item = new ItemStack(material);

        if (parts.length > 1) {
            String properties = parts[1].replace("}", "");
            ItemMeta meta = item.getItemMeta();

            if (properties.contains("name=")) {
                String name = extractProperty(properties, "name");
                meta.displayName(plugin.formatText(name));
            }

            if (properties.contains("lore=")) {
                String loreStr = extractProperty(properties, "lore");
                List<net.kyori.adventure.text.Component> lore = Arrays.stream(loreStr.split(","))
                        .map(line -> plugin.formatText(line.trim().replace("'", "")))
                        .toList();
                meta.lore(lore);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private @NotNull String extractProperty(@NotNull String properties, String key) {
        int start = properties.indexOf(key + "='") + key.length() + 2;
        int end = properties.indexOf("'", start);
        if (end == -1) end = properties.length();
        return properties.substring(start, end);
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        MenuSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        if (!event.getInventory().equals(session.inventory)) return;

        event.setCancelled(true);

        int slot = event.getSlot();
        ButtonWrapper button = session.wrapper.buttons.get(slot);

        if (button != null) {
            try {
                button.method.setAccessible(true);

                Class<?>[] paramTypes = button.method.getParameterTypes();
                if (paramTypes.length == 1 && Player.class.isAssignableFrom(paramTypes[0])) {
                    button.method.invoke(button.instance, player);
                } else if (paramTypes.length == 2 &&
                        Player.class.isAssignableFrom(paramTypes[0]) &&
                        InventoryClickEvent.class.isAssignableFrom(paramTypes[1])) {
                    button.method.invoke(button.instance, player, event);
                } else {
                    button.method.invoke(button.instance);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error executing button action: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        MenuSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) return;

        if (session.wrapper.closeMethod != null) {
            try {
                session.wrapper.closeMethod.setAccessible(true);
                session.wrapper.closeMethod.invoke(session.wrapper.instance, player);
            } catch (Exception e) {
                plugin.getLogger().severe("Error executing close handler: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private record MenuWrapper(
            Object instance,
            Menu annotation,
            Map<Integer, ButtonWrapper> buttons,
            Method closeMethod
    ) {
    }

    private record ButtonWrapper(
            Method method,
            Button annotation,
            Object instance
    ) {
    }

    private record MenuSession(
            Class<?> menuClass,
            Inventory inventory,
            MenuWrapper wrapper
    ) {
    }
}