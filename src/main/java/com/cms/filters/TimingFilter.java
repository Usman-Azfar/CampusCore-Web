package com.cms.filters;

import com.cms.util.BuildInfo;
import com.cms.util.RequestTiming;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Runs first for every request: adds the X-CampusCore-Build header (which build is serving) and
 * times dynamic requests (pages and actions, not CSS/images/scripts) for the /diagnostics page.
 */
public class TimingFilter extends HttpFilter {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        response.setHeader("X-CampusCore-Build", BuildInfo.summary());
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/css/") || path.startsWith("/images/") || path.startsWith("/js/")) {
            chain.doFilter(request, response);
            return;
        }
        RequestTiming.start();
        try {
            chain.doFilter(request, response);
        } finally {
            RequestTiming.finish(request.getMethod(), path, response.getStatus());
        }
    }
}
