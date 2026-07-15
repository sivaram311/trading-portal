package com.delena.tradingportal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "paper_journal")
public class PaperJournalEntity {

    @Id
    private UUID id;

    @Column(name = "decision_id")
    private UUID decisionId;

    private String symbol;

    @Column(name = "session_date")
    private LocalDate sessionDate;

    private String status;
    private String mode;
    private String direction;
    private String grade;
    private double score;

    @Column(name = "weights_version")
    private String weightsVersion;

    private String automation;

    @Column(name = "detected_at")
    private Instant detectedAt;

    @Column(name = "actioned_at")
    private Instant actionedAt;

    @Column(name = "actioned_by")
    private String actionedBy;

    @Column(name = "action_note")
    private String actionNote;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "created_at")
    private Instant createdAt;

    protected PaperJournalEntity() {
    }

    public PaperJournalEntity(UUID id, UUID decisionId, String symbol, LocalDate sessionDate, String status,
                              String mode, String direction, String grade, double score, String weightsVersion,
                              String automation, Instant detectedAt, Instant actionedAt, String actionedBy,
                              String actionNote, String payload) {
        this.id = id;
        this.decisionId = decisionId;
        this.symbol = symbol;
        this.sessionDate = sessionDate;
        this.status = status;
        this.mode = mode;
        this.direction = direction;
        this.grade = grade;
        this.score = score;
        this.weightsVersion = weightsVersion;
        this.automation = automation;
        this.detectedAt = detectedAt;
        this.actionedAt = actionedAt;
        this.actionedBy = actionedBy;
        this.actionNote = actionNote;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getDecisionId() {
        return decisionId;
    }

    public String getStatus() {
        return status;
    }

    public String getPayload() {
        return payload;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setActionedAt(Instant actionedAt) {
        this.actionedAt = actionedAt;
    }

    public void setActionedBy(String actionedBy) {
        this.actionedBy = actionedBy;
    }

    public void setActionNote(String actionNote) {
        this.actionNote = actionNote;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
