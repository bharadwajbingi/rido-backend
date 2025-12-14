package com.rido.gateway.config

import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.config.HttpClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.netty.http.client.HttpClient
import reactor.netty.tcp.SslProvider
import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory

@Configuration
class GatewayHttpClientConfig {

    @Value("\${SPRING_CLOUD_GATEWAY_HTTPCLIENT_SSL_TRUSTED_X509_CERTIFICATES:/etc/certs/ca/ca.crt}")
    private lateinit var trustedCertPath: String

    @Value("\${SPRING_CLOUD_GATEWAY_HTTPCLIENT_SSL_CERTIFICATE:/etc/certs/gateway/gateway.crt}")
    private lateinit var clientCertPath: String

    @Value("\${SPRING_CLOUD_GATEWAY_HTTPCLIENT_SSL_CERTIFICATE_PRIVATE_KEY:/etc/certs/gateway/gateway.key}")
    private lateinit var clientKeyPath: String

    @Bean
    fun httpclientSslConfigurer(): HttpClientCustomizer {
        return HttpClientCustomizer { httpClient ->
            try {
                val sslContext = buildSslContext()
                httpClient.secure { sslContextSpec ->
                    sslContextSpec.sslContext(sslContext)
                }
            } catch (e: Exception) {
                println("⚠️  mTLS Config Failed: ${e.message}. Running in plain HTTP mode (Standalone).")
                httpClient  // Return client without SSL configuration
            }
        }
    }

    private fun buildSslContext(): io.netty.handler.ssl.SslContext {
        return try {
            // Load CA certificate for trust
            val cf = CertificateFactory.getInstance("X.509")
            val caFile = FileInputStream(trustedCertPath)
            val caCert = cf.generateCertificate(caFile) as X509Certificate
            caFile.close()

            // Create TrustManager with CA cert
            val trustKeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            trustKeyStore.load(null, null)
            trustKeyStore.setCertificateEntry("ca", caCert)

            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(trustKeyStore)

            // Build SSL context with client certificate
            SslContextBuilder.forClient()
                .trustManager(tmf)
                .keyManager(
                    FileInputStream(clientCertPath),
                    FileInputStream(clientKeyPath)
                )
                .build()
        } catch (e: Exception) {
            throw e  // Rethrow to be caught by outer handler
        }
    }
}
