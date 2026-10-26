package com.uptimewatch.metrics;

import com.uptimewatch.alert.AlertMessage;
import com.uptimewatch.alert.AlertService;
import com.uptimewatch.check.ProbeOutcome;
import com.uptimewatch.monitor.CheckResultRepository;
import com.uptimewatch.monitor.IncidentRepository;
import com.uptimewatch.monitor.Monitor;
import com.uptimewatch.monitor.MonitorRepository;
import com.uptimewatch.monitor.MonitorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics // metrics exporters are off in tests unless requested
@ActiveProfiles("test")
class UptimeWatchMetricsTest {

    @Autowired MockMvc mvc;
    @Autowired UptimeWatchMetrics metrics;
    @Autowired AlertService alerts;
    @Autowired MonitorService service;
    @Autowired MonitorRepository monitors;
    @Autowired CheckResultRepository checks;
    @Autowired IncidentRepository incidents;

    @BeforeEach
    void clean() {
        incidents.deleteAll();
        checks.deleteAll();
        monitors.deleteAll();
    }

    @Test
    void exposesUptimeWatchMetricsInPrometheusFormat() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Long shop = monitors.save(new Monitor("Shop", "https://shop.example", "Shopping", 60)).getId();
        Long docs = monitors.save(new Monitor("Docs", "https://docs.example", "Developer Tools", 60)).getId();

        service.recordResult(shop, new ProbeOutcome(now.minusSeconds(60), false, 503, 80, "HTTP 503", null));
        service.recordResult(shop, new ProbeOutcome(now, false, 503, 80, "HTTP 503", null));
        service.recordResult(docs, new ProbeOutcome(now, true, 200, 300, null, now.plus(Duration.ofDays(30))));
        metrics.recordCheck("Docs", "Developer Tools", true, 300);
        alerts.dispatch(List.of(new AlertMessage(AlertMessage.Type.DOWN, "Shop", "https://shop.example", "HTTP 503")));
        metrics.refreshGauges();

        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("uptimewatch_monitor_up{application=\"uptimewatch\",category=\"Shopping\",monitor=\"Shop\"} 0.0"),
                        containsString("uptimewatch_monitor_up{application=\"uptimewatch\",category=\"Developer Tools\",monitor=\"Docs\"} 1.0"),
                        containsString("uptimewatch_monitor_ssl_days_left{application=\"uptimewatch\",category=\"Developer Tools\",monitor=\"Docs\"} 29.0"),
                        containsString("uptimewatch_incidents_open{application=\"uptimewatch\"} 1.0"),
                        containsString("uptimewatch_check_duration_seconds_bucket{application=\"uptimewatch\",category=\"Developer Tools\",monitor=\"Docs\",result=\"up\",le=\"0.5\"} 1"),
                        containsString("uptimewatch_alerts_sent_total{application=\"uptimewatch\",channel=\"log\",outcome=\"success\",type=\"DOWN\"} 1.0"))));
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")));
    }
}
