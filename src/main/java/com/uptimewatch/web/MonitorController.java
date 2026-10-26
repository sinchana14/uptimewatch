package com.uptimewatch.web;

import com.uptimewatch.monitor.MonitorService;
import com.uptimewatch.monitor.dto.CheckView;
import com.uptimewatch.monitor.dto.CreateMonitorRequest;
import com.uptimewatch.monitor.dto.IncidentView;
import com.uptimewatch.monitor.dto.MonitorSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class MonitorController {

    private final MonitorService service;

    public MonitorController(MonitorService service) {
        this.service = service;
    }

    @GetMapping("/monitors")
    public List<MonitorSummary> list() {
        return service.listSummaries();
    }

    @GetMapping("/monitors/{id}/checks")
    public List<CheckView> checks(@PathVariable Long id,
                                  @RequestParam(defaultValue = "60") int limit) {
        return service.recentChecks(id, clamp(limit, 500));
    }

    @GetMapping("/monitors/{id}/incidents")
    public List<IncidentView> monitorIncidents(@PathVariable Long id,
                                               @RequestParam(defaultValue = "10") int limit) {
        return service.incidentsForMonitor(id, clamp(limit, 100));
    }

    @GetMapping("/incidents")
    public List<IncidentView> incidents(@RequestParam(defaultValue = "20") int limit) {
        return service.recentIncidents(clamp(limit, 100));
    }

    /** Requires the X-Api-Key header (see ApiKeyInterceptor). */
    @PostMapping("/monitors")
    @ResponseStatus(HttpStatus.CREATED)
    public MonitorSummary create(@Valid @RequestBody CreateMonitorRequest request) {
        return service.create(request);
    }

    /** Requires the X-Api-Key header (see ApiKeyInterceptor). */
    @DeleteMapping("/monitors/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    private static int clamp(int value, int max) {
        return Math.max(1, Math.min(value, max));
    }
}
