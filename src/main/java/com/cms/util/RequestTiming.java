package com.cms.util;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Where a request's time goes: total time, time spent waiting for a database connection, time
 * spent in SQL statements and how many statements ran. Filled in by TimingFilter and DBConnection
 * for the current thread; the last requests are kept in memory for the /diagnostics page.
 */
public final class RequestTiming {

    /** One finished request. */
    public static final class Entry {
        public final Instant at;
        public final String method, path;
        public final int status, queries;
        public final double totalMs, dbMs, connMs;

        Entry(Instant at, String method, String path, int status, double totalMs, double dbMs, double connMs, int queries) {
            this.at = at;
            this.method = method;
            this.path = path;
            this.status = status;
            this.totalMs = totalMs;
            this.dbMs = dbMs;
            this.connMs = connMs;
            this.queries = queries;
        }

        /** Time not spent on the database: Java code, page rendering, sending the response. */
        public double appMs() {
            return Math.max(0, totalMs - dbMs - connMs);
        }
    }

    private static final int KEEP = 100;
    private static final Deque<Entry> RECENT = new ArrayDeque<>();

    // [0] start nanos, [1] statement nanos, [2] connection-wait nanos, [3] statement count
    private static final ThreadLocal<long[]> CURRENT = new ThreadLocal<>();

    private RequestTiming() {
    }

    public static void start() {
        CURRENT.set(new long[] { System.nanoTime(), 0, 0, 0 });
    }

    public static void addStatement(long nanos) {
        long[] t = CURRENT.get();
        if (t != null) {
            t[1] += nanos;
            t[3]++;
        }
    }

    public static void addConnectionWait(long nanos) {
        long[] t = CURRENT.get();
        if (t != null)
            t[2] += nanos;
    }

    /** Ends the current request's timing and keeps it with the recent ones. */
    public static void finish(String method, String path, int status) {
        long[] t = CURRENT.get();
        CURRENT.remove();
        if (t == null)
            return;
        Entry e = new Entry(Instant.now(), method, path, status, (System.nanoTime() - t[0]) / 1e6, t[1] / 1e6, t[2] / 1e6, (int) t[3]);
        synchronized (RECENT) {
            RECENT.addFirst(e);
            while (RECENT.size() > KEEP)
                RECENT.removeLast();
        }
    }

    /** The most recent requests, newest first. */
    public static List<Entry> recent() {
        synchronized (RECENT) {
            return new ArrayList<>(RECENT);
        }
    }
}
