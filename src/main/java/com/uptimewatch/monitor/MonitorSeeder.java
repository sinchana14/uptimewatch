package com.uptimewatch.monitor;

import com.uptimewatch.config.UptimeWatchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Adds the configured starter monitors on first run so the status page is never empty. */
@Component
public class MonitorSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MonitorSeeder.class);

    private final MonitorRepository monitors;
    private final UptimeWatchProperties properties;

    public MonitorSeeder(MonitorRepository monitors, UptimeWatchProperties properties) {
        this.monitors = monitors;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (monitors.count() > 0) {
            return;
        }
        properties.seedMonitors().forEach(seed ->
                monitors.save(new Monitor(seed.name(), seed.url(), seed.category(), 60)));
        log.info("Seeded {} starter monitors", properties.seedMonitors().size());
    }
}
