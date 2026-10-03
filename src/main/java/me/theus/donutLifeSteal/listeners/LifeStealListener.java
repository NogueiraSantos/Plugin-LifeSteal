package me.theus.donutLifeSteal.listeners;

import me.theus.donutLifeSteal.DonutLifeSteal;
import me.theus.donutLifeSteal.managers.LifeStealManager;
import me.theus.donutLifeSteal.models.LifeStealUser;
import me.theus.donutLifeSteal.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class LifeStealListener implements Listener {

    private final DonutLifeSteal plugin;

    public LifeStealListener(DonutLifeSteal plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getManager().loadUser(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getManager().unloadUser(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                LifeStealUser user = plugin.getManager().getUser(player.getUniqueId());
                if (user != null) {
                    plugin.getManager().applyPlayerHearts(player, user.getHearts());
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null || killer.equals(victim)) {
            return;
        }

        if (plugin.getConfig().getBoolean("ANTI-ALT.ENABLED", true)) {
            String victimIp = victim.getAddress() != null && victim.getAddress().getAddress() != null ? victim.getAddress().getAddress().getHostAddress() : "";
            String killerIp = killer.getAddress() != null && killer.getAddress().getAddress() != null ? killer.getAddress().getAddress().getHostAddress() : "";

            if (!victimIp.isEmpty() && victimIp.equalsIgnoreCase(killerIp)) {
                killer.sendMessage(plugin.getMessage("ALT-IP-BLOCKED", "&cEste jogador está bloqueado por conta do IP"));
                return;
            }
        }

        LifeStealUser victimUser = plugin.getManager().getUser(victim.getUniqueId());
        if (victimUser == null) {
            victimUser = plugin.getManager().loadUser(victim);
        }

        int minHearts = plugin.getConfig().getInt("MIN-HEARTS", plugin.getConfig().getInt("HEART-DEFAULT.HEART-PER-PLAYER", 4));
        int currentHearts = victimUser.getHearts();

        if (currentHearts > minHearts) {
            victimUser.setHearts(currentHearts - 1);
            plugin.getDatabase().saveUser(victimUser);

            ItemStack heartItem = plugin.getManager().createHeartItem(killer.getUniqueId());
            victim.getWorld().dropItemNaturally(victim.getLocation(), heartItem);
        } else {
            if (plugin.getConfig().getBoolean("DROP-HEART-WHEN-VICTIM-AT-BASE", false)) {
                ItemStack heartItem = plugin.getManager().createHeartItem(killer.getUniqueId());
                victim.getWorld().dropItemNaturally(victim.getLocation(), heartItem);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        String heartDisplayName = Utils.color(plugin.getConfig().getString("HEART-ITEM.DISPLAY-NAME", "&#FF0000Coração Partido"));
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            if (item.getItemMeta().getDisplayName().equals(heartDisplayName) && !plugin.getManager().isHeartItem(item)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(plugin.getMessage("INVALID-HEART", "&cEste coração é inválido ou já foi utilizado!"));
                return;
            }
        }

        if (!plugin.getManager().isHeartItem(item)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        org.bukkit.inventory.EquipmentSlot hand = event.getHand();
        if (hand == null) hand = org.bukkit.inventory.EquipmentSlot.HAND;

        LifeStealManager.HeartValidationResult result = plugin.getManager().validateAndConsumeHeart(player, item, hand);

        switch (result) {
            case SUCCESS:
                player.sendMessage(plugin.getMessage("HEART-CLAIMED", "&fAdicionado &#FF0000+1 Coração"));
                try {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                } catch (Throwable ignored) {}
                break;

            case MAX_HEARTS_REACHED:
                player.sendMessage(plugin.getMessage("MAX-HEARTS-REACHED", "&cVocê já atingiu o limite máximo de corações!"));
                break;

            case INVALID_DUPE:
            case ALREADY_CLAIMED_OR_INVALID:
                if (hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
                    player.getInventory().setItemInOffHand(null);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
                player.sendMessage(plugin.getMessage("INVALID-HEART", "&cEste coração é inválido ou já foi utilizado!"));
                break;

            default:
                break;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!plugin.getConfig().getBoolean("ANTI-DUPE.BLOCK-ANVIL-RENAME", true)) {
            return;
        }

        ItemStack result = event.getResult();
        if (result == null || !result.hasItemMeta()) {
            return;
        }

        String heartDisplayName = Utils.color(plugin.getConfig().getString("HEART-ITEM.DISPLAY-NAME", "&#FF0000Coração Partido"));
        ItemMeta meta = result.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.getDisplayName().equalsIgnoreCase(heartDisplayName)) {
            if (!plugin.getManager().isHeartItem(result)) {
                event.setResult(null);
                return;
            }
        }

        ItemStack first = event.getInventory().getItem(0);
        if (plugin.getManager().isHeartItem(first)) {
            event.setResult(null);
        }
    }
}
