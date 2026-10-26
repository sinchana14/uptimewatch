package com.uptimewatch.monitor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonitorRepository extends JpaRepository<Monitor, Long> {

    List<Monitor> findAllByOrderByNameAsc();

    List<Monitor> findByEnabledTrue();
}
