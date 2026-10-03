package me.theus.donutLifeSteal.models;

import java.util.UUID;

public class LifeStealUser {

    private final UUID uuid;
    private String username;
    private int hearts;
    private String lastIp = "";
    private long lastUpdated;

    public LifeStealUser(UUID uuid) {
        this(uuid, "", 4);
    }

    public LifeStealUser(UUID uuid, String username, int hearts) {
        this.uuid = uuid;
        this.username = username;
        this.hearts = hearts;
        this.lastUpdated = System.currentTimeMillis();
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
        this.lastUpdated = System.currentTimeMillis();
    }

    public int getHearts() {
        return hearts;
    }

    public void setHearts(int hearts) {
        this.hearts = hearts;
        this.lastUpdated = System.currentTimeMillis();
    }

    public String getLastIp() {
        return lastIp;
    }

    public void setLastIp(String lastIp) {
        this.lastIp = lastIp;
        this.lastUpdated = System.currentTimeMillis();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public double getMaxHealthPoints() {
        return hearts * 2.0;
    }
}
