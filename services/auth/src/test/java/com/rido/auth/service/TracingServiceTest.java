package com.rido.auth.service;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TracingServiceTest {

    @InjectMocks
    private TracingService tracingService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String DEVICE_ID = "device-123";
    private static final String IP = "192.168.1.1";
    private static final UUID JTI = UUID.randomUUID();

    @Nested
    @DisplayName("Tag Current Span Tests")
    class TagCurrentSpan {

        @Test
        @DisplayName("Should tag span when span is valid")
        void shouldTagSpan_whenSpanValid() {
            Span mockSpan = mock(Span.class);
            SpanContext validContext = SpanContext.create(
                    "0123456789abcdef0123456789abcdef",
                    "0123456789abcdef",
                    TraceFlags.getSampled(),
                    TraceState.getDefault()
            );
            when(mockSpan.getSpanContext()).thenReturn(validContext);

            try (MockedStatic<Span> spanMock = mockStatic(Span.class)) {
                spanMock.when(Span::current).thenReturn(mockSpan);

                tracingService.tagCurrentSpan(USER_ID, DEVICE_ID, IP, JTI);

                verify(mockSpan).setAttribute("auth.user_id", USER_ID.toString());
                verify(mockSpan).setAttribute("auth.device_id", DEVICE_ID);
                verify(mockSpan).setAttribute("auth.ip", IP);
                verify(mockSpan).setAttribute("auth.jti", JTI.toString());
            }
        }

        @Test
        @DisplayName("Should skip tagging when span is invalid")
        void shouldSkipTagging_whenSpanInvalid() {
            Span mockSpan = mock(Span.class);
            when(mockSpan.getSpanContext()).thenReturn(SpanContext.getInvalid());

            try (MockedStatic<Span> spanMock = mockStatic(Span.class)) {
                spanMock.when(Span::current).thenReturn(mockSpan);

                tracingService.tagCurrentSpan(USER_ID, DEVICE_ID, IP, JTI);

                verify(mockSpan, never()).setAttribute(anyString(), anyString());
            }
        }

        @Test
        @DisplayName("Should handle null values gracefully")
        void shouldHandleNullValues_gracefully() {
            Span mockSpan = mock(Span.class);
            SpanContext validContext = SpanContext.create(
                    "0123456789abcdef0123456789abcdef",
                    "0123456789abcdef",
                    TraceFlags.getSampled(),
                    TraceState.getDefault()
            );
            when(mockSpan.getSpanContext()).thenReturn(validContext);

            try (MockedStatic<Span> spanMock = mockStatic(Span.class)) {
                spanMock.when(Span::current).thenReturn(mockSpan);

                assertThatCode(() -> tracingService.tagCurrentSpan(null, null, null, null))
                        .doesNotThrowAnyException();

                verify(mockSpan, never()).setAttribute(eq("auth.user_id"), anyString());
                verify(mockSpan, never()).setAttribute(eq("auth.device_id"), anyString());
                verify(mockSpan, never()).setAttribute(eq("auth.ip"), anyString());
                verify(mockSpan, never()).setAttribute(eq("auth.jti"), anyString());
            }
        }
    }
}
