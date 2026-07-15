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
@Table(name = "gann_snapshot")
public class GannSnapshotEntity {

    @Id
    private UUID id;
    private String symbol;
    private Instant asof;
    private String killzone;

    @Column(name = "gann_bias")
    private String gannBias;

    private int quality;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "created_at")
    private Instant createdAt;

    protected GannSnapshotEntity() {
    }

    public GannSnapshotEntity(UUID id, String symbol, Instant asof, String killzone,
                              String gannBias, int quality, String payload) {
        this.id = id;
        this.symbol = symbol;
        this.asof = asof;
        this.killzone = killzone;
        this.gannBias = gannBias;
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
