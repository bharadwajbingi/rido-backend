package com.rido.auth.security;

import com.rido.auth.crypto.JwtKeyStore;
import com.rido.auth.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Component
public class JwtUserAuthenticationFilter extends OncePerRequestFilter {

    private final JwtKeyStore keyStore;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtUserAuthenticationFilter(JwtKeyStore keyStore,
                                       TokenBlacklistService tokenBlacklistService) {
        this.keyStore = keyStore;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {

        String auth = req.getHeader("Authorization");

        // No JWT => allow request to continue (for login/register/etc.)
        if (auth == null || !auth.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }

        String token = auth.substring(7);

        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKeyResolver(keyStore.getResolver())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // ---------------------------------------------
            //  BLACKLIST CHECK
            // ---------------------------------------------
            String jti = claims.getId();
            if (tokenBlacklistService.isBlacklisted(jti)) {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            // Store user info for controllers
            req.setAttribute("userId", claims.getSubject());
            List<String> roles = (List<String>) claims.get("roles");
            req.setAttribute("roles", roles);
            req.setAttribute("role", roles.isEmpty() ? null : roles.get(0));
            req.setAttribute("jti", jti);

            // Populate SecurityContext
            var authorities = roles.stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        chain.doFilter(req, res);
    }
}
