package com.example.fxraptor.repository;

import com.example.fxraptor.domain.TriggerOrder;
import com.example.fxraptor.domain.TriggerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TriggerOrderRepository extends JpaRepository<TriggerOrder, Long> {

    List<TriggerOrder> findAllByStatus(TriggerStatus status);

    @Modifying
    @Query("""
            update TriggerOrder t
            set t.status = :nextStatus, t.updatedAt = :updatedAt
            where t.id = :id and t.status = :currentStatus
            """)
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("currentStatus") TriggerStatus currentStatus,
                              @Param("nextStatus") TriggerStatus nextStatus,
                              @Param("updatedAt") Instant updatedAt);
}
