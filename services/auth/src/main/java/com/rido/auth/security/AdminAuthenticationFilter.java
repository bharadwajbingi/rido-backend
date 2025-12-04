package com.rido.auth.security;

import com.rido.auth.crypto.JwtKeyStore;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter for admin endpoints on port 9090.
 * Only validates tokens on authenticated admin endpoints.
 * Verifies the user has ADMIN role.
 */
@Component
public class AdminAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthenticationFilter.class);

    private final JwtKeyStore keyStore;

    public AdminAuthenticationFilter(JwtKeyStore keyStore) {
        this.keyStore = keyStore;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip authentication for public admin endpoints
        if (isPublicAdminEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only apply to /admin/* paths
        if (!path.startsWith("/admin")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract and validate JWT
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKeyResolver(keyStore.getResolver())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String userId = claims.getSubject();
            
            // Get roles from JWT (stored as a list)
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            String role = (roles != null && !roles.isEmpty()) ? roles.get(0) : null;

            // Verify ADMIN role
            if (role == null || !role.equals("ADMIN")) {
                log.warn("admin_auth_denied: User {} attempted admin access with roles {}", userId, roles);
                sendError(response, HttpServletResponse.SC_FORBIDDEN, "Admin access required");
                return;
            }

            // Set request attributes for downstream use
            request.setAttribute("userId", userId);
            request.setAttribute("role", role);
            request.setAttribute("jti", claims.getId());

            // Set Spring Security context
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
            var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.warn("admin_auth_failed: {}", e.getMessage());
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
        }
    }

    private boolean isPublicAdminEndpoint(String path) {
        return path.equals("/admin/login") ||
               path.equals("/admin/health");
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
