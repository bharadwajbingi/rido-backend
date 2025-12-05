package com.rido.auth.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
public class InputSanitizationFilter implements Filter {

    private static final Pattern XSS_PATTERN = Pattern.compile(
            "<script>|javascript:|onload=|onerror=|eval\\(|alert\\(",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SQL_PATTERN = Pattern.compile(
            "('|--|;|/\\*|\\*/|xp_)",
            Pattern.CASE_INSENSITIVE
    );

    private static final long MAX_REQUEST_SIZE = 10 * 1024 * 1024; // 10MB

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        long contentLength = httpRequest.getContentLengthLong();
        if (contentLength > MAX_REQUEST_SIZE) {
            httpResponse.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            httpResponse.getWriter().write("Request body too large");
            return;
        }

        chain.doFilter(new SanitizedRequest(httpRequest), response);
    }

    private static class SanitizedRequest extends HttpServletRequestWrapper {
        public SanitizedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            return sanitize(super.getParameter(name));
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) return null;
            String[] sanitized = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                sanitized[i] = sanitize(values[i]);
            }
            return sanitized;
        }

        @Override
        public String getHeader(String name) {
            String value = super.getHeader(name);
            if ("Authorization".equalsIgnoreCase(name)) {
                return value;
            }
            return sanitize(value);
        }

        private String sanitize(String input) {
            if (input == null) return null;
            String sanitized = XSS_PATTERN.matcher(input).replaceAll("");
            sanitized = SQL_PATTERN.matcher(sanitized).replaceAll("");
            return sanitized;
        }
    }
}
