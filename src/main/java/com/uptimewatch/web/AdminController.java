package com.uptimewatch.web;

import com.uptimewatch.alert.AlertMessage;
import com.uptimewatch.alert.AlertService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin-only helpers. Every POST here is protected by ApiKeyInterceptor. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AlertService alerts;

    public AdminController(AlertService alerts) {
        this.alerts = alerts;
    }

    /** Lets the status page confirm an admin key before showing admin controls. */
    @PostMapping("/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify() {
    }

    /** Sends a test message through every enabled alert channel, e.g. to check Telegram setup. */
    @PostMapping("/test-alert")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void testAlert() {
        alerts.dispatch(List.of(new AlertMessage(AlertMessage.Type.RECOVERED, "UptimeWatch test",
                "https://example.com", "Test alert: your alert channel is working.")));
    }
}
