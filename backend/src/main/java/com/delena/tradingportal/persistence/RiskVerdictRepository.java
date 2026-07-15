package com.delena.tradingportal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RiskVerdictRepository extends JpaRepository<RiskVerdictEntity, UUID> {

    Optional<RiskVerdictEntity> findTopByDecisionIdOrderByTsDesc(UUID decisionId);
}
