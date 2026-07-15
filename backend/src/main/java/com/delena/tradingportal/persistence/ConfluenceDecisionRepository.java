package com.delena.tradingportal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConfluenceDecisionRepository extends JpaRepository<ConfluenceDecisionEntity, UUID> {

    Optional<ConfluenceDecisionEntity> findTopByOrderByTsDesc();
}
