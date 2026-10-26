package com.uptimewatch.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uptimewatch.config.UptimeWatchProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends alerts to a Telegram chat through the Bot API. Enabled when
 * TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID are set.
 */
@Component
public class TelegramNotifier implements Notifier {

    private final UptimeWatchProperties.Telegram config;
    private final String publicUrl;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public TelegramNotifier(UptimeWatchProperties properties, ObjectMapper json) {
        this.config = properties.telegram();
        this.publicUrl = properties.publicUrl();
        this.json = json;
    }

    @Override
    public String name() {
        return "telegram";
    }

    @Override
    public boolean isEnabled() {
        return config.enabled();
    }

    @Override
    public void send(AlertMessage message) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", config.chatId());
        body.put("text", format(message, publicUrl));
        body.put("parse_mode", "HTML");
        body.put("disable_web_page_preview", true);

        String base = config.apiBase() == null || config.apiBase().isBlank()
                ? "https://api.telegram.org" : config.apiBase();
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/bot" + config.botToken() + "/sendMessage"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            // Never include the request URL here: it contains the bot token.
            throw new IllegalStateException("Telegram answered HTTP " + response.statusCode());
        }
    }

    static String format(AlertMessage m, String publicUrl) {
        String heading = switch (m.type()) {
            case DOWN -> "🔴 <b>DOWN</b>";
            case RECOVERED -> "🟢 <b>RECOVERED</b>";
            case SSL_EXPIRING -> "⚠️ <b>SSL EXPIRING</b>";
        };
        StringBuilder text = new StringBuilder()
                .append(heading).append(": ").append(escape(m.monitorName())).append('\n')
                .append(escape(m.url())).append('\n')
                .append(escape(m.detail()));
        if (publicUrl != null && !publicUrl.isBlank()) {
            text.append("\n\n<a href=\"").append(escape(publicUrl)).append("\">Open status page</a>");
        }
        return text.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
