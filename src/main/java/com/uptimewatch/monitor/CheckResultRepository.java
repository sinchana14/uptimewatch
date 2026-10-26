package com.uptimewatch.monitor;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {

    @Query("""
            select c from CheckResult c
            where c.monitor.id = :monitorId
            order by c.checkedAt desc
            """)
    List<CheckResult> findRecent(@Param("monitorId") Long monitorId, Pageable pageable);

    /** Returns [total checks, successful checks] for a monitor since the given time. */
    @Query("""
            select count(c), coalesce(sum(case when c.up = true then 1 else 0 end), 0)
            from CheckResult c
            where c.monitor.id = :monitorId and c.checkedAt >= :since
            """)
    List<Object[]> countChecksSince(@Param("monitorId") Long monitorId, @Param("since") Instant since);

    /** Average response time of successful checks since the given time (null when none). */
    @Query("""
            select avg(c.responseTimeMs) from CheckResult c
            where c.monitor.id = :monitorId and c.up = true and c.checkedAt >= :since
            """)
    Double averageResponseSince(@Param("monitorId") Long monitorId, @Param("since") Instant since);

    @Modifying
    @Query("delete from CheckResult c where c.checkedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("delete from CheckResult c where c.monitor.id = :monitorId")
    void deleteByMonitorId(@Param("monitorId") Long monitorId);
}
