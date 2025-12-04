package com.rido.gateway.exception

import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class GlobalErrorHandler : ErrorWebExceptionHandler {
    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        val response = exchange.response
        response.headers.contentType = MediaType.APPLICATION_JSON

        val bytes = """{"error":"${ex.message}"}""".toByteArray()
        val buffer = response.bufferFactory().wrap(bytes)

        return response.writeWith(Mono.just(buffer))
    }
}
