package com.capstone.adproject.repositories;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.capstone.adproject.model.AiUsageEvent;

public interface AiUsageEventRepository extends JpaRepository<AiUsageEvent, Long> {

    /** Everything from a moment on, oldest first. The page asks for at most 90 days. */
    List<AiUsageEvent> findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(LocalDateTime from);

    List<AiUsageEvent> findTop25ByOrderByOccurredAtDescIdDesc();

    /** Estimated spend from a moment on (requests with no price count as nothing here). */
    @Query("SELECT COALESCE(SUM(e.costUsd), 0) FROM AiUsageEvent e WHERE e.occurredAt >= :from")
    BigDecimal sumCostSince(@Param("from") LocalDateTime from);

    long deleteByOccurredAtBefore(LocalDateTime before);
}
