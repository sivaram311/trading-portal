package com.delena.tradingportal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IctSnapshotRepository extends JpaRepository<IctSnapshotEntity, UUID> {

    Optional<IctSnapshotEntity> findTopByOrderByAsofDesc();
}
