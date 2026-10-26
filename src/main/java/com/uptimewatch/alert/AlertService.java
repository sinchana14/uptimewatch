package com.uptimewatch.alert;

import com.uptimewatch.metrics.UptimeWatchMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** Fans an alert out to every enabled channel. One failing channel never blocks the others. */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final List<Notifier> notifiers;
    private final UptimeWatchMetrics metrics;

    public AlertService(List<Notifier> notifiers, UptimeWatchMetrics metrics) {
        this.notifiers = notifiers;
        this.metrics = metrics;
        notifiers.forEach(n -> log.info("Alert channel '{}' is {}", n.name(), n.isEnabled() ? "enabled" : "disabled"));
    }

    public void dispatch(List<AlertMessage> messages) {
        for (AlertMessage message : messages) {
            for (Notifier notifier : notifiers) {
                if (!notifier.isEnabled()) {
                    continue;
                }
                try {
                    notifier.send(message);
                    metrics.recordAlert(message.type().name(), notifier.name(), true);
                } catch (Exception e) {
                    metrics.recordAlert(message.type().name(), notifier.name(), false);
                    log.error("Could not send {} alert for {} via {}: {}", message.type(), message.monitorName(),
                            notifier.name(), e.getMessage());
                }
            }
        }
    }
}
