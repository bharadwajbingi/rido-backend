package com.rido.auth.config;

import org.apache.catalina.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SSL/TLS Configuration for Auth Service.
 * 
 * When AUTH_STANDALONE_MODE=true:
 * - Disables SSL on main connector (port from SERVER_PORT env var)
 * - Allows plain HTTP for testing/development
 * - Certificate loading is skipped
 * 
 * In production (standalone=false):
 * - SSL is enabled via Spring Boot auto-configuration
 * - mTLS is enforced for Gateway communication
 */
@Configuration
public class SslConfig {
    
    private static final Logger log = LoggerFactory.getLogger(SslConfig.class);
    
    @Value("${auth.standalone.enabled:false}")
    private boolean standaloneMode;
    
    @Value("${server.port:8443}")
    private int serverPort;
    
    /**
     * When standalone mode is enabled, configure the main connector to use HTTP instead of HTTPS.
     * This overrides any SSL configuration from environment variables.
     */
    @Bean
    @ConditionalOnProperty(name = "auth.standalone.enabled", havingValue = "true")
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> standaloneHttpCustomizer() {
        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.warn("⚠️  STANDALONE MODE ENABLED - SSL/TLS DISABLED");
        log.warn("   Main port {} will accept plain HTTP", serverPort);
        log.warn("   mTLS filter disabled in SecurityConfig");
        log.warn("   ❌ DO NOT USE IN PRODUCTION!");
        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        return factory -> {
            // Clear any SSL configuration
            factory.setSsl(null);
            
            log.info("✓ Configured standalone HTTP connector on port {}", serverPort);
        };
    }
}
