package com.uptimewatch.alert;

/** A channel alerts can be delivered to (log, Telegram, and later e-mail or Slack). */
public interface Notifier {

    String name();

    boolean isEnabled();

    void send(AlertMessage message) throws Exception;
}
