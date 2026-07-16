package com.delena.tradingportal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConfluenceDecisionRepository extends JpaRepository<ConfluenceDecisionEntity, UUID> {

    Optional<ConfluenceDecisionEntity> findTopByOrderByTsDesc();

    Optional<ConfluenceDecisionEntity> findTopByDirectionOrderByTsDesc(String direction);

    @Query("SELECT DISTINCT d.weightsVersion FROM ConfluenceDecisionEntity d ORDER BY d.weightsVersion")
    List<String> findDistinctWeightsVersions();
}
