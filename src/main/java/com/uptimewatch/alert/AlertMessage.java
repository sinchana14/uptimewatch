package com.uptimewatch.alert;

/** A notification-worthy event about one monitor. */
public record AlertMessage(Type type, String monitorName, String url, String detail) {

    public enum Type { DOWN, RECOVERED, SSL_EXPIRING }
}
