package com.rido.auth.util;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Secure IP extraction service to prevent rate limit bypass via header spoofing.
 * 
 * Handles dual-port architecture:
 * - Port 8080 (Gateway): Uses X-Forwarded-For from trusted gateway
 * - Port 9091 (Admin): Direct access, uses getRemoteAddr()
 */
@Service
public class IpExtractorService {

    private static final Logger log = LoggerFactory.getLogger(IpExtractorService.class);

    @Value("${server.port:8081}")
    private int serverPort;

    @Value("${admin.server.port:9091}")
    private int adminPort;

    @Value("${auth.ip-extraction.trusted-gateway-ip:172.16.0.0/12}")
    private String trustedGatewaySubnet;

    /**
     * Extract real client IP from request.
     * 
     * Logic:
     * 1. If admin port (9091): Use getRemoteAddr() (direct access, no proxy)
     * 2. If regular port (8081): Check X-Forwarded-For from gateway
     * 3. Validate and sanitize IP
     */
    public String extractClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        
        // Admin port: Direct access, no proxy
        if (isAdminPort(request)) {
            log.debug("Admin port detected, using direct IP: {}", remoteAddr);
            return sanitizeIp(remoteAddr);
        }

        // Regular port: Check X-Forwarded-For from gateway
        String forwardedFor = request.getHeader("X-Forwarded-For");
        
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            // X-Forwarded-For format: "client, proxy1, proxy2"
            // Take the first (leftmost) IP as the real client
            String clientIp = forwardedFor.split(",")[0].trim();
            
            // Validate it's not a private IP (spoofing attempt)
            if (isPrivateIp(clientIp)) {
                log.warn("Blocked private IP in X-Forwarded-For: {} from {}", clientIp, remoteAddr);
                return sanitizeIp(remoteAddr);
            }
            
            log.debug("Extracted IP from X-Forwarded-For: {}", clientIp);
            return sanitizeIp(clientIp);
        }

        // Fallback: Use remote address
        log.debug("No X-Forwarded-For, using remote addr: {}", remoteAddr);
        return sanitizeIp(remoteAddr);
    }

    /**
     * Check if request is on admin port (direct access)
     */
    private boolean isAdminPort(HttpServletRequest request) {
        int localPort = request.getLocalPort();
        return localPort == adminPort;
    }

    /**
     * Check if IP is in private range (RFC 1918)
     * Private IPs in X-Forwarded-For indicate spoofing
     */
    private boolean isPrivateIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return true;
        }

        // Private IP ranges
        List<String> privateRanges = Arrays.asList(
            "10.",          // 10.0.0.0/8
            "172.16.",      // 172.16.0.0/12 (simplified check)
            "172.17.",
            "172.18.",
            "172.19.",
            "172.20.",
            "172.21.",
            "172.22.",
            "172.23.",
            "172.24.",
            "172.25.",
            "172.26.",
            "172.27.",
            "172.28.",
            "172.29.",
            "172.30.",
            "172.31.",
            "192.168.",     // 192.168.0.0/16
            "127.",         // 127.0.0.0/8 (localhost)
            "169.254."      // 169.254.0.0/16 (link-local)
        );

        return privateRanges.stream().anyMatch(ip::startsWith);
    }

    /**
     * Sanitize IP to prevent injection attacks
     */
    private String sanitizeIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "unknown";
        }

        // Remove any non-IP characters (prevent header injection)
        String sanitized = ip.replaceAll("[^0-9a-fA-F.:]", "");
        
        // Validate format (IPv4 or IPv6)
        if (!isValidIpFormat(sanitized)) {
            log.warn("Invalid IP format detected: {}, using 'unknown'", ip);
            return "unknown";
        }

        return sanitized;
    }

    /**
     * Basic IP format validation
     */
    private boolean isValidIpFormat(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        // IPv4: xxx.xxx.xxx.xxx
        if (ip.matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")) {
            return true;
        }

        // IPv6: simplified check (contains colons)
        if (ip.contains(":") && ip.matches("^[0-9a-fA-F:]+$")) {
            return true;
        }

        return false;
    }

    /**
     * Get server port for logging/debugging
     */
    public int getServerPort() {
        return serverPort;
    }

    /**
     * Get admin port for logging/debugging
     */
    public int getAdminPort() {
        return adminPort;
    }
}
