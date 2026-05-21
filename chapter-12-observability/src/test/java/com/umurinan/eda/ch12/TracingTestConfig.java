package com.umurinan.eda.ch12;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.otel.bridge.Slf4JBaggageEventListener;
import io.micrometer.tracing.otel.bridge.Slf4JEventListener;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Collections;

/**
 * Provides a fully wired OTel-backed tracing stack for tests.
 *
 * Spring Boot's tracing auto-configuration requires a working OTLP exporter
 * endpoint. In tests we replace it with an in-memory SDK that exports nothing
 * but correctly injects W3C {@code traceparent} headers.
 */
@TestConfiguration
public class TracingTestConfig {

    @Bean
    @Primary
    public OpenTelemetrySdk openTelemetrySdk() {
        return OpenTelemetrySdk.builder()
                .setTracerProvider(
                        SdkTracerProvider.builder()
                                .setSampler(Sampler.alwaysOn())
                                .build())
                .setPropagators(
                        ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
    }

    @Bean
    @Primary
    public OtelCurrentTraceContext otelCurrentTraceContext() {
        return new OtelCurrentTraceContext();
    }

    @Bean
    @Primary
    public Slf4JEventListener slf4JEventListener() {
        return new Slf4JEventListener();
    }

    @Bean
    @Primary
    public Slf4JBaggageEventListener slf4JBaggageEventListener() {
        return new Slf4JBaggageEventListener(Collections.emptyList());
    }

    @Bean
    @Primary
    public Tracer tracer(OpenTelemetrySdk openTelemetrySdk,
                         OtelCurrentTraceContext otelCurrentTraceContext,
                         Slf4JEventListener slf4JEventListener,
                         Slf4JBaggageEventListener slf4JBaggageEventListener) {
        var otelTracer = openTelemetrySdk.getTracer("test-tracer");
        var baggageManager = new OtelBaggageManager(otelCurrentTraceContext,
                Collections.emptyList(), Collections.emptyList());
        return new OtelTracer(otelTracer, otelCurrentTraceContext, event -> {
            slf4JEventListener.onEvent(event);
            slf4JBaggageEventListener.onEvent(event);
        }, baggageManager);
    }

    @Bean
    @Primary
    public Propagator propagator(OpenTelemetrySdk openTelemetrySdk) {
        return new OtelPropagator(
                openTelemetrySdk.getPropagators(),
                openTelemetrySdk.getTracer("test-propagator"));
    }

    @Bean
    @Primary
    public ObservationRegistry observationRegistry(Tracer tracer, Propagator propagator) {
        var registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new DefaultTracingObservationHandler(tracer));
        return registry;
    }
}
