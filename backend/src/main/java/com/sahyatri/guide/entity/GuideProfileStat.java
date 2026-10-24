package com.sahyatri.guide.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.UUID;

/**
 * One pick in a guide's stats row (§7.21.8): which number, never its value, except a CUSTOM stat, which keeps what the
 * admin typed. Replaced as a set by an admin.
 */
@Entity
@Table(name = "guide_profile_stats")
@IdClass(GuideProfileStat.Key.class)
public class GuideProfileStat {

    @Id
    private UUID guideId;

    @Id
    private int position;

    @Enumerated(EnumType.STRING)
    private GuideStatKey statKey;

    /** The route for ROUTE_TIMES; null otherwise. */
    private UUID routeId;

    /** What a CUSTOM stat shows, as the admin typed it; null for every other kind. */
    private String customValue;

    private String customLabel;

    protected GuideProfileStat() {
    }

    public GuideProfileStat(UUID guideId, int position, GuideStatKey statKey, UUID routeId, String customValue,
                            String customLabel) {
        this.guideId = guideId;
        this.position = position;
        this.statKey = statKey;
        this.routeId = routeId;
        this.customValue = customValue;
        this.customLabel = customLabel;
    }

    public int getPosition() {
        return position;
    }

    public GuideStatKey getStatKey() {
        return statKey;
    }

    public UUID getRouteId() {
        return routeId;
    }

    public String getCustomValue() {
        return customValue;
    }

    public String getCustomLabel() {
        return customLabel;
    }

    public record Key(UUID guideId, int position) implements Serializable {
    }
}
