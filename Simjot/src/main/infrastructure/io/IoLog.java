package main.infrastructure.io;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight I/O logging with simple throttling to avoid noisy output.
 */
public final class IoLog {
    private IoLog() {}

    private static final Map<String, Long> LAST_LOG_MS = new ConcurrentHashMap<>();
    private static final long THROTTLE_MS = 2000L;

    public static void info(String key, String msg) {
        log("INFO", key, msg, null);
    }

    public static void warn(String key, String msg, Throwable t) {
        log("WARN", key, msg, t);
    }

    private static void log(String level, String key, String msg, Throwable t) {
        if (key != null) {
            long now = System.currentTimeMillis();
            Long last = LAST_LOG_MS.get(key);
            if (last != null && now - last < THROTTLE_MS) return;
            LAST_LOG_MS.put(key, now);
        }
        String prefix = "[" + level + "][" + Instant.now() + "]";
        if ("WARN".equals(level)) {
            System.err.println(prefix + " " + msg);
            if (t != null) t.printStackTrace(System.err);
        } else {
            System.out.println(prefix + " " + msg);
        }
    }
}
