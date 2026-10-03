package me.theus.donutLifeSteal.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static String color(String message) {
        if (message == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of("#" + matcher.group(1)).toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static Component parseComponent(String text) {
        if (text == null) return Component.empty();
        try {
            return MiniMessage.miniMessage().deserialize(text);
        } catch (Throwable t) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        }
    }

    public static void setPlayerMaxHealth(Player player, double health) {
        if (player == null) return;
        Attribute attr = resolveMaxHealthAttribute();
        if (attr != null && player.getAttribute(attr) != null) {
            player.getAttribute(attr).setBaseValue(health);
        } else {
            player.setMaxHealth(health);
        }
    }

    public static double getPlayerMaxHealth(Player player) {
        if (player == null) return 20.0;
        Attribute attr = resolveMaxHealthAttribute();
        if (attr != null && player.getAttribute(attr) != null) {
            return player.getAttribute(attr).getBaseValue();
        }
        return player.getMaxHealth();
    }

    private static Attribute resolveMaxHealthAttribute() {
        try {
            return Attribute.valueOf("MAX_HEALTH");
        } catch (Throwable t) {
            try {
                return Attribute.valueOf("GENERIC_MAX_HEALTH");
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}
