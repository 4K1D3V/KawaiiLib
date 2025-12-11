package dev.oumaimaa.kawaiilib.managers.discord;

import dev.oumaimaa.kawaiilib.Bootstrap;
import dev.oumaimaa.kawaiilib.annotations.DiscordBot;
import dev.oumaimaa.kawaiilib.annotations.DiscordCommand;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DiscordManager extends ListenerAdapter {

    private final Bootstrap plugin;
    private final JDA jda;
    private final Map<String, Method> commandMethods = new ConcurrentHashMap<>();
    private final Object mainInstance;

    public DiscordManager(Bootstrap plugin, @NotNull DiscordBot config) {
        this.plugin = plugin;

        try {
            this.mainInstance = plugin.getMainClass().getDeclaredConstructor().newInstance();

            this.jda = JDABuilder.createLight(config.token())
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.DIRECT_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT
                    )
                    .addEventListeners(this)
                    .build();

            jda.awaitReady();

            plugin.getLogger().info("Discord bot connected successfully!");

            registerDiscordCommands();

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Discord bot", e);
        }
    }

    private void registerDiscordCommands() {
        Reflections reflections = new Reflections(plugin.getMainClass().getPackageName());
        Set<Method> discordCommands = reflections.getMethodsAnnotatedWith(DiscordCommand.class);

        for (Method method : discordCommands) {
            DiscordCommand ann = method.getAnnotation(DiscordCommand.class);
            commandMethods.put(ann.name(), method);

            // Register slash command
            jda.updateCommands()
                    .addCommands(Commands.slash(ann.name(), "Command from plugin"))
                    .queue();

            plugin.getLogger().info("Registered Discord command: /" + ann.name());
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        Method method = commandMethods.get(commandName);

        if (method != null) {
            try {
                method.setAccessible(true);

                Class<?>[] paramTypes = method.getParameterTypes();
                if (paramTypes.length == 1 &&
                        SlashCommandInteractionEvent.class.isAssignableFrom(paramTypes[0])) {
                    method.invoke(mainInstance, event);
                } else {
                    method.invoke(mainInstance);
                    event.reply("Command executed!").queue();
                }

            } catch (Exception e) {
                plugin.getLogger().severe("Error executing Discord command: " + e.getMessage());
                e.printStackTrace();
                event.reply("An error occurred while executing the command.").setEphemeral(true).queue();
            }
        }
    }

    public JDA getJda() {
        return jda;
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
            plugin.getLogger().info("Discord bot disconnected");
        }
    }
}