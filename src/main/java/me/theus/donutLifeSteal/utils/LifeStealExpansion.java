package me.theus.donutLifeSteal.utils;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.theus.donutLifeSteal.DonutLifeSteal;
import me.theus.donutLifeSteal.models.LifeStealUser;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class LifeStealExpansion extends PlaceholderExpansion {

    private final DonutLifeSteal plugin;

    public LifeStealExpansion(DonutLifeSteal plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "donutlifesteal";
    }

    @Override
    public @NotNull String getAuthor() {
        return "theus";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || player.getUniqueId() == null) {
            return "";
        }

        LifeStealUser user = plugin.getManager().getUser(player.getUniqueId());
        int defaultHearts = plugin.getConfig().getInt("HEART-DEFAULT.HEART-PER-PLAYER", 4);
        int hearts = user != null ? user.getHearts() : defaultHearts;

        String param = params.toLowerCase();

        switch (param) {
            case "hearts":
            case "coracoes":
            case "vida":
                return String.valueOf(hearts);

            case "health":
            case "max_health":
                return String.valueOf((int) (hearts * 2));

            case "max_hearts":
                return String.valueOf(plugin.getConfig().getInt("MAX-HEARTS", 20));

            case "min_hearts":
                return String.valueOf(plugin.getConfig().getInt("MIN-HEARTS", 4));

            default:
                return null;
        }
    }
}
