package com.uptimewatch.check;

import com.uptimewatch.config.UptimeWatchProperties;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Performs a single HTTP GET against a URL and reports whether it is up.
 * A site counts as up when it answers with a 2xx or 3xx status within the timeout.
 */
@Component
public class HttpChecker {

    static final String USER_AGENT = "UptimeWatch/0.1 (uptime monitor)";
    private static final int MAX_ERROR_LENGTH = 500;

    private final HttpClient client;
    private final Duration timeout;

    public HttpChecker(UptimeWatchProperties properties) {
        this.timeout = Duration.ofMillis(properties.defaultTimeoutMs());
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public ProbeOutcome check(String url) {
        // Millisecond precision matches what every supported database can store exactly.
        Instant startedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            long elapsedMs = elapsedMs(start);
            int code = response.statusCode();
            boolean up = code >= 200 && code < 400;
            String error = up ? null : "HTTP " + code;
            Instant sslExpiresAt = response.sslSession().map(HttpChecker::certificateExpiry).orElse(null);
            return new ProbeOutcome(startedAt, up, code, elapsedMs, error, sslExpiresAt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failure(startedAt, start, e);
        } catch (Exception e) {
            return failure(startedAt, start, e);
        }
    }

    private static ProbeOutcome failure(Instant startedAt, long start, Exception e) {
        String message = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        if (message.length() > MAX_ERROR_LENGTH) {
            message = message.substring(0, MAX_ERROR_LENGTH);
        }
        return new ProbeOutcome(startedAt, false, null, elapsedMs(start), message, null);
    }

    private static Instant certificateExpiry(SSLSession session) {
        try {
            Certificate[] chain = session.getPeerCertificates();
            if (chain.length > 0 && chain[0] instanceof X509Certificate leaf) {
                return leaf.getNotAfter().toInstant();
            }
        } catch (Exception ignored) {
            // Certificate details are a bonus; a missing chain should not fail the check.
        }
        return null;
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
