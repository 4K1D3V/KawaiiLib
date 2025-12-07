package dev.oumaimaa.kawaiilib.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public sealed interface TextUtils permits TextUtils.Impl {

    @Contract(" -> new")
    static @NotNull TextUtils create() {
        return new Impl();
    }

    Component parse(String input);

    Component gradient(String text, String fromColor, String toColor);

    final class Impl implements TextUtils {
        private final MiniMessage miniMessage = MiniMessage.miniMessage();
        private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

        @Override
        public @NotNull Component parse(@NotNull String input) {
            if (input.contains("&")) {
                return legacy.deserialize(input);
            }
            return miniMessage.deserialize(input);
        }

        @Override
        public @NotNull Component gradient(String text, String fromColor, String toColor) {
            // Simple gradient simulation
            return miniMessage.deserialize("<gradient:" + fromColor + ":" + toColor + ">" + text + "</gradient>");
        }
    }
}