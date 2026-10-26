package com.uptimewatch.check;

import com.sun.net.httpserver.HttpServer;
import com.uptimewatch.config.UptimeWatchProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HttpCheckerTest {

    private HttpServer server;
    private String baseUrl;
    private final HttpChecker checker = new HttpChecker(
            new UptimeWatchProperties("k", 1000, 1, 2000, 30, 2, 14, null, null, List.of()));

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        respondWith("/ok", 200);
        respondWith("/broken", 503);
        server.createContext("/moved", exchange -> {
            exchange.getResponseHeaders().add("Location", "/ok");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/agent", exchange -> {
            boolean ours = HttpChecker.USER_AGENT.equals(exchange.getRequestHeaders().getFirst("User-Agent"));
            exchange.sendResponseHeaders(ours ? 200 : 400, -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void healthyEndpointIsUp() {
        ProbeOutcome outcome = checker.check(baseUrl + "/ok");

        assertThat(outcome.up()).isTrue();
        assertThat(outcome.statusCode()).isEqualTo(200);
        assertThat(outcome.error()).isNull();
        assertThat(outcome.responseTimeMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void serverErrorIsDown() {
        ProbeOutcome outcome = checker.check(baseUrl + "/broken");

        assertThat(outcome.up()).isFalse();
        assertThat(outcome.statusCode()).isEqualTo(503);
        assertThat(outcome.error()).isEqualTo("HTTP 503");
    }

    @Test
    void redirectsAreFollowed() {
        ProbeOutcome outcome = checker.check(baseUrl + "/moved");

        assertThat(outcome.up()).isTrue();
        assertThat(outcome.statusCode()).isEqualTo(200);
    }

    @Test
    void sendsIdentifyingUserAgent() {
        assertThat(checker.check(baseUrl + "/agent").statusCode()).isEqualTo(200);
    }

    @Test
    void unreachableHostIsDownWithError() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        ProbeOutcome outcome = checker.check("http://127.0.0.1:" + closedPort + "/");

        assertThat(outcome.up()).isFalse();
        assertThat(outcome.statusCode()).isNull();
        assertThat(outcome.error()).isNotBlank();
    }

    private void respondWith(String path, int status) {
        server.createContext(path, exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
    }
}
