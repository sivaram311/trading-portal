package com.delena.tradingportal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaperJournalRepository extends JpaRepository<PaperJournalEntity, UUID>,
        JpaSpecificationExecutor<PaperJournalEntity> {

    long countByStatus(String status);

    @Query("SELECT COUNT(j) FROM PaperJournalEntity j WHERE j.status IN ('PAPER_OPEN', 'PARTIAL')")
    long countOpenPositions();

    @Query("SELECT COUNT(j) FROM PaperJournalEntity j WHERE j.status IN ('LIVE_OPEN', 'LIVE_PARTIAL')")
    long countLiveOpenPositions();

    List<PaperJournalEntity> findByStatusIn(List<String> statuses);

    boolean existsByDecisionId(UUID decisionId);

    Optional<PaperJournalEntity> findTopByDecisionIdOrderByCreatedAtDesc(UUID decisionId);

    @Query("SELECT COUNT(DISTINCT j.decisionId) FROM PaperJournalEntity j")
    long countDistinctDecisionIds();

    @Query("SELECT COUNT(DISTINCT j.sessionDate) FROM PaperJournalEntity j")
    long countDistinctSessionDays();

    @Query("SELECT DISTINCT j.weightsVersion FROM PaperJournalEntity j ORDER BY j.weightsVersion")
    List<String> findDistinctWeightsVersions();
}
