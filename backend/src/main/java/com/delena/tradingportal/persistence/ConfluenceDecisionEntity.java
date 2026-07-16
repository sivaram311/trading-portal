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
@Table(name = "confluence_decision")
public class ConfluenceDecisionEntity {

    @Id
    private UUID id;
    private String symbol;
    private Instant ts;
    private String mode;
    private String direction;
    private String grade;
    private double score;
    private String agreement;
    private String automation;

    @Column(name = "weights_version")
    private String weightsVersion;

    @Column(name = "ict_ref")
    private String ictRef;

    @Column(name = "gann_ref")
    private String gannRef;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "created_at")
    private Instant createdAt;

    protected ConfluenceDecisionEntity() {
    }

    public ConfluenceDecisionEntity(UUID id, String symbol, Instant ts, String mode, String direction,
                                    String grade, double score, String agreement, String automation,
                                    String weightsVersion, String ictRef, String gannRef, String payload) {
        this.id = id;
        this.symbol = symbol;
        this.ts = ts;
        this.mode = mode;
        this.direction = direction;
        this.grade = grade;
        this.score = score;
        this.agreement = agreement;
        this.automation = automation;
        this.weightsVersion = weightsVersion;
        this.ictRef = ictRef;
        this.gannRef = gannRef;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Instant getTs() {
        return ts;
    }

    public String getMode() {
        return mode;
    }

    public String getDirection() {
        return direction;
    }

    public String getGrade() {
        return grade;
    }

    public String getWeightsVersion() {
        return weightsVersion;
    }

    public String getAutomation() {
        return automation;
    }

    public String getPayload() {
        return payload;
    }
}
