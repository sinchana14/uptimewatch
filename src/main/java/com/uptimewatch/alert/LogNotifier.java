package com.uptimewatch.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Always-on channel: every alert lands in the application log. */
@Component
public class LogNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger("ALERTS");

    @Override
    public String name() {
        return "log";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void send(AlertMessage m) {
        switch (m.type()) {
            case DOWN -> log.warn("DOWN: {} ({}) - {}", m.monitorName(), m.url(), m.detail());
            case RECOVERED -> log.info("RECOVERED: {} ({}) - {}", m.monitorName(), m.url(), m.detail());
            case SSL_EXPIRING -> log.warn("SSL EXPIRING: {} ({}) - {}", m.monitorName(), m.url(), m.detail());
        }
    }
}
