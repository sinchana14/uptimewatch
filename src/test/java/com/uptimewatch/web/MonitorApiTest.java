package com.uptimewatch.web;

import com.uptimewatch.alert.AlertMessage;
import com.uptimewatch.check.ProbeOutcome;
import com.uptimewatch.monitor.CheckResultRepository;
import com.uptimewatch.monitor.IncidentRepository;
import com.uptimewatch.monitor.Monitor;
import com.uptimewatch.monitor.MonitorRepository;
import com.uptimewatch.monitor.MonitorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MonitorApiTest {

    private static final String KEY = "test-key";

    @Autowired MockMvc mvc;
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
    void writeEndpointsRequireTheApiKey() throws Exception {
        mvc.perform(post("/api/monitors").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Site\",\"url\":\"https://example.com\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/monitors").header("X-Api-Key", "wrong").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Site\",\"url\":\"https://example.com\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/admin/verify")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/verify").header("X-Api-Key", KEY)).andExpect(status().isNoContent());
    }

    @Test
    void createsAndListsMonitorsWithCategory() throws Exception {
        mvc.perform(post("/api/monitors").header("X-Api-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Example","url":"https://example.com","category":"Demo","intervalSeconds":120}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Example"))
                .andExpect(jsonPath("$.category").value("Demo"))
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.intervalSeconds").value(120));

        mvc.perform(post("/api/monitors").header("X-Api-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No category\",\"url\":\"https://example.org\"}"))
                .andExpect(jsonPath("$.category").value("Other"));

        mvc.perform(get("/api/monitors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void rejectsInvalidMonitors() throws Exception {
        mvc.perform(post("/api/monitors").header("X-Api-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"url\":\"ftp://example.com\",\"intervalSeconds\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.url").exists())
                .andExpect(jsonPath("$.fields.intervalSeconds").exists());
    }

    @Test
    void outageOpensAndResolvesAnIncidentAndProducesAlerts() throws Exception {
        Long id = monitors.save(new Monitor("Shop", "https://shop.example", "Shopping", 60)).getId();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        assertThat(service.recordResult(id, ok(now.minusSeconds(300)))).isEmpty();
        assertThat(service.recordResult(id, ok(now.minusSeconds(240)))).isEmpty();
        // First failure is treated as a blip: no alert yet.
        assertThat(service.recordResult(id, failed(now.minusSeconds(180)))).isEmpty();

        List<AlertMessage> down = service.recordResult(id, failed(now.minusSeconds(120)));
        assertThat(down).singleElement().satisfies(a -> {
            assertThat(a.type()).isEqualTo(AlertMessage.Type.DOWN);
            assertThat(a.detail()).contains("HTTP 503").contains("2 failed checks");
        });

        mvc.perform(get("/api/monitors"))
                .andExpect(jsonPath("$[0].status").value("DOWN"))
                .andExpect(jsonPath("$[0].uptime24h").value(50.0))
                .andExpect(jsonPath("$[0].avgResponseMs24h").value(120))
                .andExpect(jsonPath("$[0].recentChecks", hasSize(4)));

        mvc.perform(get("/api/incidents"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ongoing").value(true))
                .andExpect(jsonPath("$[0].monitorName").value("Shop"))
                .andExpect(jsonPath("$[0].startedAt").value(startsWith(
                        now.minusSeconds(180).toString().substring(0, 16))));

        List<AlertMessage> recovered = service.recordResult(id, ok(now.minusSeconds(60)));
        assertThat(recovered).singleElement().satisfies(a -> {
            assertThat(a.type()).isEqualTo(AlertMessage.Type.RECOVERED);
            assertThat(a.detail()).startsWith("Back up after 2 min");
        });

        mvc.perform(get("/api/monitors/" + id + "/incidents"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ongoing").value(false))
                .andExpect(jsonPath("$[0].durationSeconds").value(120));
    }

    @Test
    void deletesMonitorWithItsHistoryAndIncidents() throws Exception {
        Long id = monitors.save(new Monitor("Old", "https://old.example", "Other", 60)).getId();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        service.recordResult(id, failed(now.minusSeconds(60)));
        service.recordResult(id, failed(now));
        assertThat(incidents.count()).isEqualTo(1);

        mvc.perform(delete("/api/monitors/" + id).header("X-Api-Key", KEY))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/monitors")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/monitors/" + id + "/checks")).andExpect(status().isNotFound());
        assertThat(incidents.count()).isZero();
    }

    private static ProbeOutcome ok(Instant at) {
        return new ProbeOutcome(at, true, 200, 120, null, null);
    }

    private static ProbeOutcome failed(Instant at) {
        return new ProbeOutcome(at, false, 503, 90, "HTTP 503", null);
    }
}
