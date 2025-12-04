package com.rido.auth.service;

import io.opentelemetry.api.trace.Span;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TracingService {

    public void tagCurrentSpan(UUID userId, String deviceId, String ip, UUID jti) {
        Span span = Span.current();
        if (!span.getSpanContext().isValid()) return;

        if (userId != null) span.setAttribute("auth.user_id", userId.toString());
        if (deviceId != null) span.setAttribute("auth.device_id", deviceId);
        if (ip != null) span.setAttribute("auth.ip", ip);
        if (jti != null) span.setAttribute("auth.jti", jti.toString());
    }
}
