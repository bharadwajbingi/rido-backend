package com.rido.auth.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures a second HTTP connector on port 9090 for admin endpoints.
 * This port does NOT use SSL/TLS - intended for access via VPN/SSH tunnel only.
 */
@Configuration
public class AdminServerConfig {

    @Value("${admin.server.port:9090}")
    private int adminPort;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> adminConnectorCustomizer() {
        return factory -> {
            Connector adminConnector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            adminConnector.setScheme("http");
            adminConnector.setPort(adminPort);
            adminConnector.setSecure(false);
            factory.addAdditionalTomcatConnectors(adminConnector);
        };
    }
}
