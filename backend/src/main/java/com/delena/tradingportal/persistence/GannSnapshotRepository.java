package com.delena.tradingportal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GannSnapshotRepository extends JpaRepository<GannSnapshotEntity, UUID> {

    Optional<GannSnapshotEntity> findTopByOrderByAsofDesc();
}
