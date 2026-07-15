package com.delena.tradingportal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface PaperJournalRepository extends JpaRepository<PaperJournalEntity, UUID>,
        JpaSpecificationExecutor<PaperJournalEntity> {

    long countByStatus(String status);

    boolean existsByDecisionId(UUID decisionId);

    Optional<PaperJournalEntity> findTopByDecisionIdOrderByCreatedAtDesc(UUID decisionId);
}
