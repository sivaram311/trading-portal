package com.delena.tradingportal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OhlcCandleRepository extends JpaRepository<OhlcCandleEntity, Long> {

    List<OhlcCandleEntity> findBySymbolAndTfOrderByTsAsc(String symbol, String tf);

    List<OhlcCandleEntity> findBySymbolAndTfAndTsBetweenOrderByTsAsc(String symbol, String tf, Instant from, Instant to);

    long countBySymbolAndTf(String symbol, String tf);

    Optional<OhlcCandleEntity> findTopBySymbolAndTfOrderByTsDesc(String symbol, String tf);
}
