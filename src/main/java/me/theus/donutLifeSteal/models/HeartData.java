package me.theus.donutLifeSteal.models;

import java.util.UUID;

public class HeartData {

    private final UUID heartId;
    private final UUID creatorUuid;
    private final long createdAt;
    private String status; // ACTIVE, CONSUMED, INVALIDATED
    private UUID claimedBy;
    private long claimedAt;

    public HeartData(UUID heartId, UUID creatorUuid) {
        this(heartId, creatorUuid, System.currentTimeMillis(), "ACTIVE", null, 0L);
    }

    public HeartData(UUID heartId, UUID creatorUuid, long createdAt, String status, UUID claimedBy, long claimedAt) {
        this.heartId = heartId;
        this.creatorUuid = creatorUuid;
        this.createdAt = createdAt;
        this.status = status != null ? status : "ACTIVE";
        this.claimedBy = claimedBy;
        this.claimedAt = claimedAt;
    }

    public UUID getHeartId() {
        return heartId;
    }

    public UUID getCreatorUuid() {
        return creatorUuid;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getClaimedBy() {
        return claimedBy;
    }

    public void setClaimedBy(UUID claimedBy) {
        this.claimedBy = claimedBy;
    }

    public long getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(long claimedAt) {
        this.claimedAt = claimedAt;
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
