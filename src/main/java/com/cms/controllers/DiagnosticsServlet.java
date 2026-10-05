package com.cms.controllers;

import com.cms.dao.DBConnection;
import com.cms.util.BuildInfo;
import com.cms.util.PasswordHasher;
import com.cms.util.RequestTiming;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin only: plain-text performance report. Which build is running, the connection pool, the
 * network round trip to the database, a CPU benchmark, and where the time of recent requests went
 * (database vs. application). Shows no credentials or host names.
 */
public class DiagnosticsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (AdminSupport.requireAdmin(request, response) == null)
            return;
        response.setContentType("text/plain;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();

        out.println("CampusCore diagnostics");
        out.println("======================");
        out.println("Build: " + BuildInfo.summary());
        Runtime rt = Runtime.getRuntime();
        out.printf(Locale.ROOT, "JVM: Java %s, %d CPU(s) visible, max heap %d MB, uptime %d min%n",
                System.getProperty("java.version"), rt.availableProcessors(), rt.maxMemory() / (1024 * 1024),
                ManagementFactory.getRuntimeMXBean().getUptime() / 60000);

        int[] pool = DBConnection.poolStats();
        out.println(pool == null ? "Pool: not started yet"
                : String.format(Locale.ROOT, "Pool: HikariCP, %d active, %d idle, %d open (max %d), %d waiting",
                        pool[0], pool[1], pool[2], DBConnection.maxPoolSize(), pool[3]));

        // Network round trip: the same tiny query several times on one pooled connection
        out.println();
        out.println("Database round trip (SELECT 1 on one pooled connection):");
        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT 1")) {
            List<String> times = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                long t0 = System.nanoTime();
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                }
                times.add(String.format(Locale.ROOT, "%.1f", (System.nanoTime() - t0) / 1e6));
            }
            out.println("  " + String.join(" / ", times) + " ms");
        } catch (Exception e) {
            out.println("  failed: " + e.getClass().getSimpleName());
        }

        // CPU: one password hash (the same work a login does)
        long t0 = System.nanoTime();
        PasswordHasher.hash("diagnostics-benchmark");
        out.printf(Locale.ROOT, "%nCPU: one password hash (PBKDF2, %s) took %.0f ms%n", "600,000 iterations", (System.nanoTime() - t0) / 1e6);

        // Recent requests: averages per path, then the latest ones
        List<RequestTiming.Entry> recent = RequestTiming.recent();
        recent.removeIf(e -> e.path.startsWith("/diagnostics"));
        out.println();
        out.println("Recent requests by page (averages; app = Java code and page rendering):");
        out.println(String.format(Locale.ROOT, "  %-28s %5s %9s %9s %9s %9s %8s", "page", "count", "total ms", "db ms", "conn ms", "app ms", "queries"));
        Map<String, List<RequestTiming.Entry>> byPath = new LinkedHashMap<>();
        for (RequestTiming.Entry e : recent)
            byPath.computeIfAbsent(e.method + " " + e.path, k -> new ArrayList<>()).add(e);
        for (Map.Entry<String, List<RequestTiming.Entry>> g : byPath.entrySet()) {
            List<RequestTiming.Entry> l = g.getValue();
            out.println(String.format(Locale.ROOT, "  %-28s %5d %9.0f %9.0f %9.0f %9.0f %8.1f", trim(g.getKey()), l.size(),
                    avg(l, 0), avg(l, 1), avg(l, 2), avg(l, 3), l.stream().mapToInt(e -> e.queries).average().orElse(0)));
        }
        out.println();
        out.println("Latest requests:");
        for (int i = 0; i < Math.min(25, recent.size()); i++) {
            RequestTiming.Entry e = recent.get(i);
            out.println(String.format(Locale.ROOT, "  %s  %-28s %3d  total %6.0f  db %6.0f  conn %5.0f  app %6.0f  ms  %3d queries",
                    e.at.toString().substring(11, 19), trim(e.method + " " + e.path), e.status, e.totalMs, e.dbMs, e.connMs, e.appMs(), e.queries));
        }
    }

    private static String trim(String s) {
        return s.length() <= 28 ? s : s.substring(0, 27) + "~";
    }

    private static double avg(List<RequestTiming.Entry> l, int what) {
        return l.stream().mapToDouble(e -> what == 0 ? e.totalMs : what == 1 ? e.dbMs : what == 2 ? e.connMs : e.appMs()).average().orElse(0);
    }
}
