package me.theus.donutLifeSteal.managers;

import me.theus.donutLifeSteal.DonutLifeSteal;
import me.theus.donutLifeSteal.models.HeartData;
import me.theus.donutLifeSteal.models.LifeStealUser;
import me.theus.donutLifeSteal.utils.Utils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LifeStealManager {

    private final DonutLifeSteal plugin;
    private final Map<UUID, LifeStealUser> users = new ConcurrentHashMap<>();
    private final Map<UUID, HeartData> heartCache = new ConcurrentHashMap<>();

    private final NamespacedKey keyHeartId;
    private final NamespacedKey keyIsHeart;
    private final NamespacedKey keyCreatedAt;

    public enum HeartValidationResult {
        SUCCESS,
        NOT_A_HEART,
        MAX_HEARTS_REACHED,
        INVALID_DUPE,
        ALREADY_CLAIMED_OR_INVALID
    }

    public LifeStealManager(DonutLifeSteal plugin) {
        this.plugin = plugin;
        this.keyHeartId = new NamespacedKey(plugin, "heart_id");
        this.keyIsHeart = new NamespacedKey(plugin, "is_heart");
        this.keyCreatedAt = new NamespacedKey(plugin, "created_at");
    }

    public LifeStealUser getUser(UUID uuid) {
        return users.get(uuid);
    }

    public LifeStealUser loadUser(Player player) {
        int defaultHearts = plugin.getConfig().getInt("HEART-DEFAULT.HEART-PER-PLAYER", 4);
        LifeStealUser user = plugin.getDatabase().loadUser(player.getUniqueId(), player.getName(), defaultHearts);
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            user.setLastIp(player.getAddress().getAddress().getHostAddress());
        }
        users.put(player.getUniqueId(), user);
        applyPlayerHearts(player, user.getHearts());
        return user;
    }

    public void unloadUser(UUID uuid) {
        LifeStealUser user = users.remove(uuid);
        if (user != null) {
            plugin.getDatabase().saveUser(user);
        }
    }

    public void saveAllUsers() {
        for (LifeStealUser user : users.values()) {
            plugin.getDatabase().saveUser(user);
        }
    }

    public void applyPlayerHearts(Player player, int hearts) {
        if (player == null) return;
        double maxHealthPoints = Math.max(2.0, hearts * 2.0);
        Utils.setPlayerMaxHealth(player, maxHealthPoints);
    }

    /**
     * Gera um novo item de coração com identificador único global (UUID)
     * e o registra no banco de dados e cache para proteção Anti-Dupe.
     */
    public ItemStack createHeartItem(UUID creatorUuid) {
        String matName = plugin.getConfig().getString("HEART-ITEM.MATERIAL", "RED_DYE");
        Material material = Material.matchMaterial(matName);
        if (material == null) material = Material.RED_DYE;

        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displayName = plugin.getConfig().getString("HEART-ITEM.DISPLAY-NAME", "&#FF0000Coração Partido");
            meta.setDisplayName(Utils.color(displayName));

            List<String> rawLore = plugin.getConfig().getStringList("HEART-ITEM.LORE");
            List<String> coloredLore = new ArrayList<>();
            for (String line : rawLore) {
                coloredLore.add(Utils.color(line));
            }
            meta.setLore(coloredLore);

            if (plugin.getConfig().contains("HEART-ITEM.CUSTOM-MODEL-DATA")) {
                int cmd = plugin.getConfig().getInt("HEART-ITEM.CUSTOM-MODEL-DATA", 0);
                if (cmd > 0) {
                    meta.setCustomModelData(cmd);
                }
            }

            String itemModel = plugin.getConfig().getString("HEART-ITEM.ITEM-MODEL", "");
            if (itemModel != null && !itemModel.trim().isEmpty()) {
                try {
                    org.bukkit.NamespacedKey modelKey = org.bukkit.NamespacedKey.fromString(itemModel.trim());
                    if (modelKey != null) {
                        meta.setItemModel(modelKey);
                    }
                } catch (Throwable ignored) {}
            }

            UUID heartId = UUID.randomUUID();
            long now = System.currentTimeMillis();

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keyIsHeart, PersistentDataType.BYTE, (byte) 1);
            pdc.set(keyHeartId, PersistentDataType.STRING, heartId.toString());
            pdc.set(keyCreatedAt, PersistentDataType.LONG, now);

            item.setItemMeta(meta);

            HeartData heartData = new HeartData(heartId, creatorUuid, now, "ACTIVE", null, 0L);
            heartCache.put(heartId, heartData);
            plugin.getDatabase().registerHeart(heartData);
        }

        return item;
    }

    public boolean isHeartItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Byte isHeart = pdc.get(keyIsHeart, PersistentDataType.BYTE);
        String heartIdStr = pdc.get(keyHeartId, PersistentDataType.STRING);
        return isHeart != null && isHeart == 1 && heartIdStr != null && !heartIdStr.isEmpty();
    }

    public UUID getHeartId(ItemStack item) {
        if (!isHeartItem(item)) return null;
        try {
            String str = item.getItemMeta().getPersistentDataContainer().get(keyHeartId, PersistentDataType.STRING);
            return str != null ? UUID.fromString(str) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Valida um coração clicado, aplicando regras de Anti-Dupe e limites de vida.
     */
    public HeartValidationResult validateAndConsumeHeart(Player player, ItemStack item) {
        return validateAndConsumeHeart(player, item, org.bukkit.inventory.EquipmentSlot.HAND);
    }

    public HeartValidationResult validateAndConsumeHeart(Player player, ItemStack item, org.bukkit.inventory.EquipmentSlot hand) {
        if (!isHeartItem(item)) {
            return HeartValidationResult.NOT_A_HEART;
        }

        UUID heartId = getHeartId(item);
        if (heartId == null) {
            return HeartValidationResult.INVALID_DUPE;
        }

        HeartData data = heartCache.get(heartId);
        if (data == null) {
            data = plugin.getDatabase().loadHeart(heartId);
            if (data != null) {
                heartCache.put(heartId, data);
            }
        }

        if (data == null) {
            plugin.getLogger().warning("[Anti-Dupe] Jogador " + player.getName() + " tentou usar coração não registrado: " + heartId);
            return HeartValidationResult.INVALID_DUPE;
        }

        if (!data.isActive()) {
            plugin.getLogger().warning("[Anti-Dupe] DUPE DETECTADO! Jogador " + player.getName() + " tentou usar coração com status: " + data.getStatus() + " (ID: " + heartId + ")");
            data.setStatus("INVALIDATED");
            plugin.getDatabase().updateHeartStatus(heartId, "INVALIDATED", player.getUniqueId(), System.currentTimeMillis());
            return HeartValidationResult.ALREADY_CLAIMED_OR_INVALID;
        }

        LifeStealUser user = getUser(player.getUniqueId());
        if (user == null) {
            user = loadUser(player);
        }

        int maxHearts = plugin.getConfig().getInt("MAX-HEARTS", 20);
        if (user.getHearts() >= maxHearts) {
            return HeartValidationResult.MAX_HEARTS_REACHED;
        }

        data.setStatus("CONSUMED");
        data.setClaimedBy(player.getUniqueId());
        data.setClaimedAt(System.currentTimeMillis());
        plugin.getDatabase().updateHeartStatus(heartId, "CONSUMED", player.getUniqueId(), System.currentTimeMillis());

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            if (hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }

        user.setHearts(user.getHearts() + 1);
        applyPlayerHearts(player, user.getHearts());

        double currentHealth = player.getHealth();
        double newMaxHealth = Utils.getPlayerMaxHealth(player);
        player.setHealth(Math.min(newMaxHealth, currentHealth + 2.0));

        plugin.getDatabase().saveUser(user);

        return HeartValidationResult.SUCCESS;
    }

    public void invalidateHeart(UUID heartId) {
        if (heartId == null) return;
        HeartData data = heartCache.get(heartId);
        if (data != null) {
            data.setStatus("INVALIDATED");
        }
        plugin.getDatabase().updateHeartStatus(heartId, "INVALIDATED", null, System.currentTimeMillis());
    }
}
