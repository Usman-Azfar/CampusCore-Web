package com.cms.util;

import java.io.InputStream;
import java.util.Properties;

/** Which build is running: version and build time (from campuscore-build.properties) and, on Render, the commit. */
public final class BuildInfo {

    private static final Properties PROPS = new Properties();
    static {
        try (InputStream in = BuildInfo.class.getClassLoader().getResourceAsStream("campuscore-build.properties")) {
            if (in != null)
                PROPS.load(in);
        } catch (Exception ignored) {
            // shown as "unknown"
        }
    }

    private BuildInfo() {
    }

    public static String version() {
        return PROPS.getProperty("version", "unknown");
    }

    public static String built() {
        return PROPS.getProperty("built", "unknown");
    }

    /** The deployed commit when the host provides it (Render sets RENDER_GIT_COMMIT), else null. */
    public static String commit() {
        String c = System.getenv("RENDER_GIT_COMMIT");
        return c == null || c.isBlank() ? null : c.substring(0, Math.min(7, c.length()));
    }

    /** e.g. "1.0.0; built 2026-10-05 12:30 UTC; commit 08578be; pool" */
    public static String summary() {
        String c = commit();
        return version() + "; built " + built() + (c != null ? "; commit " + c : "") + "; pool";
    }
}
