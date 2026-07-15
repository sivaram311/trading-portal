package com.delena.tradingportal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OhlcCandleRepository extends JpaRepository<OhlcCandleEntity, Long> {

    List<OhlcCandleEntity> findBySymbolAndTfOrderByTsAsc(String symbol, String tf);

    List<OhlcCandleEntity> findBySymbolAndTfAndTsBetweenOrderByTsAsc(String symbol, String tf, Instant from, Instant to);

    long countBySymbolAndTf(String symbol, String tf);
}
