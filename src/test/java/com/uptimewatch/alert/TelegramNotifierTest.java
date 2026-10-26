package com.uptimewatch.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.uptimewatch.config.UptimeWatchProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramNotifierTest {

    private final ObjectMapper json = new ObjectMapper();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private HttpServer fakeTelegram;
    private volatile int status = 200;

    @BeforeEach
    void start() throws IOException {
        fakeTelegram = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeTelegram.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        fakeTelegram.start();
    }

    @AfterEach
    void stop() {
        fakeTelegram.stop(0);
    }

    @Test
    void disabledWithoutTokenAndChat() {
        assertThat(notifier(null, null).isEnabled()).isFalse();
        assertThat(notifier("token", "").isEnabled()).isFalse();
        assertThat(notifier("token", "42").isEnabled()).isTrue();
    }

    @Test
    void postsFormattedMessageToTheBotApi() throws Exception {
        notifier("123:abc", "42").send(new AlertMessage(AlertMessage.Type.DOWN, "Shop <Prod>",
                "https://shop.example", "HTTP 503 (2 failed checks in a row)"));

        assertThat(path.get()).isEqualTo("/bot123:abc/sendMessage");
        JsonNode sent = json.readTree(body.get());
        assertThat(sent.get("chat_id").asText()).isEqualTo("42");
        assertThat(sent.get("parse_mode").asText()).isEqualTo("HTML");
        assertThat(sent.get("text").asText())
                .startsWith("🔴 <b>DOWN</b>: Shop &lt;Prod&gt;")
                .contains("HTTP 503")
                .contains("<a href=\"https://status.example\">Open status page</a>");
    }

    @Test
    void failsWithoutLeakingTheToken() {
        status = 401;
        assertThatThrownBy(() -> notifier("secret-token", "42").send(
                new AlertMessage(AlertMessage.Type.RECOVERED, "Shop", "https://shop.example", "Back up")))
                .hasMessage("Telegram answered HTTP 401")
                .hasMessageNotContaining("secret-token");
    }

    private TelegramNotifier notifier(String token, String chat) {
        String base = "http://127.0.0.1:" + fakeTelegram.getAddress().getPort();
        UptimeWatchProperties props = new UptimeWatchProperties("k", 1000, 1, 2000, 30, 2, 14,
                "https://status.example", new UptimeWatchProperties.Telegram(token, chat, base), List.of());
        return new TelegramNotifier(props, json);
    }
}
