package com.rido.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class ServiceAuthenticationFilter extends OncePerRequestFilter {

    private static final Pattern CN_PATTERN = Pattern.compile("CN=([^,]+)");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        X509Certificate[] certs = (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");

        if (certs != null && certs.length > 0) {
            X509Certificate clientCert = certs[0];
            String subjectDN = clientCert.getSubjectX500Principal().getName();
            String serviceName = extractCN(subjectDN);

            if (serviceName != null) {
                log.debug("Authenticated service via mTLS: {}", serviceName);
                request.setAttribute("X-Service-Name", serviceName);
                
                // We could also populate SecurityContext here if we want to treat services as principals
                // For now, just adding it as a request attribute for authorization policies
            } else {
                log.warn("mTLS certificate present but CN could not be extracted: {}", subjectDN);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractCN(String subjectDN) {
        Matcher matcher = CN_PATTERN.matcher(subjectDN);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
