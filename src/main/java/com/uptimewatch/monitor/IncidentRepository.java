package com.uptimewatch.monitor;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    @Query("select i from Incident i where i.monitor.id = :monitorId and i.resolvedAt is null")
    Optional<Incident> findOpen(@Param("monitorId") Long monitorId);

    @Query("select i from Incident i join fetch i.monitor order by i.startedAt desc")
    List<Incident> findRecent(Pageable pageable);

    @Query("select i from Incident i join fetch i.monitor where i.monitor.id = :monitorId order by i.startedAt desc")
    List<Incident> findRecentForMonitor(@Param("monitorId") Long monitorId, Pageable pageable);

    @Modifying
    @Query("delete from Incident i where i.monitor.id = :monitorId")
    void deleteByMonitorId(@Param("monitorId") Long monitorId);
}
