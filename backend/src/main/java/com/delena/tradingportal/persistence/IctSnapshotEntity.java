package com.delena.tradingportal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ict_snapshot")
public class IctSnapshotEntity {

    @Id
    private UUID id;
    private String symbol;
    private Instant asof;
    private String killzone;
    private int quality;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "created_at")
    private Instant createdAt;

    protected IctSnapshotEntity() {
    }

    public IctSnapshotEntity(UUID id, String symbol, Instant asof, String killzone, int quality, String payload) {
        this.id = id;
        this.symbol = symbol;
        this.asof = asof;
        this.killzone = killzone;
        this.quality = quality;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Instant getAsof() {
        return asof;
    }

    public String getPayload() {
        return payload;
    }
}
