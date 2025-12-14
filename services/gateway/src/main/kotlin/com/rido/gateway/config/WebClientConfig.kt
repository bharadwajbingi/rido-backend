package com.rido.gateway.config

import io.netty.handler.ssl.SslContextBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory

@Configuration
class WebClientConfig {

    @Value("\${SPRING_CLOUD_GATEWAY_HTTPCLIENT_SSL_TRUSTED_X509_CERTIFICATES:/etc/certs/ca/ca.crt}")
    private lateinit var trustedCertPath: String

    @Value("\${SPRING_CLOUD_GATEWAY_HTTPCLIENT_SSL_CERTIFICATE:/etc/certs/gateway/gateway.crt}")
    private lateinit var clientCertPath: String

    @Value("\${SPRING_CLOUD_GATEWAY_HTTPCLIENT_SSL_CERTIFICATE_PRIVATE_KEY:/etc/certs/gateway/gateway.key}")
    private lateinit var clientKeyPath: String

    @Bean("jwksWebClient")
    fun jwksWebClient(@Value("\${JWKS_URL}") jwksUrl: String): WebClient {
        // ⚡ If JWKS_URL is HTTP, skip mTLS configuration (Standalone Mode)
        if (jwksUrl.startsWith("http:")) {
            return WebClient.builder().build()
        }

        val sslContext = try {
            // Load CA certificate for trust
            val cf = CertificateFactory.getInstance("X.509")
            val caFile = FileInputStream(trustedCertPath)
            val caCert = cf.generateCertificate(caFile) as X509Certificate
            caFile.close()

            // Create TrustManager with CA cert
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, null)
            keyStore.setCertificateEntry("ca", caCert)

            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)

            // Build SSL context
            SslContextBuilder.forClient()
                .trustManager(tmf)
                .keyManager(
                    FileInputStream(clientCertPath),
                    FileInputStream(clientKeyPath)
                )
                .build()
        } catch (e: Exception) {
            // throw RuntimeException("Failed to configure mTLS for JWKS WebClient", e)
            println("⚠️ SSL Config Failed: ${e.message}. Falling back to plain WebClient (Standalone Mode).")
            return WebClient.builder().build()
        }

        val httpClient = HttpClient.create()
            .secure { it.sslContext(sslContext) }

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }
}
