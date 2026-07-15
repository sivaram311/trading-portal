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
@Table(name = "risk_verdict")
public class RiskVerdictEntity {

    @Id
    private UUID id;

    @Column(name = "decision_id")
    private UUID decisionId;

    private Instant ts;
    private boolean ok;
    private Double size;

    @Column(name = "risk_per_trade_pct")
    private Double riskPerTradePct;

    @Column(name = "daily_loss_r")
    private double dailyLossR;

    @Column(name = "open_positions")
    private int openPositions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deny_reasons")
    private String denyReasons;

    @JdbcTypeCode(SqlTypes.JSON)
    private String checks;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "created_at")
    private Instant createdAt;

    protected RiskVerdictEntity() {
    }

    public RiskVerdictEntity(UUID id, UUID decisionId, Instant ts, boolean ok, Double size,
                             Double riskPerTradePct, double dailyLossR, int openPositions,
                             String denyReasons, String checks, String payload) {
        this.id = id;
        this.decisionId = decisionId;
        this.ts = ts;
        this.ok = ok;
        this.size = size;
        this.riskPerTradePct = riskPerTradePct;
        this.dailyLossR = dailyLossR;
        this.openPositions = openPositions;
        this.denyReasons = denyReasons;
        this.checks = checks;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public boolean isOk() {
        return ok;
    }

    public String getPayload() {
        return payload;
    }
}
