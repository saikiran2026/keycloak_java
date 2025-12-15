package org.keycloak.quarkus.runtime.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Scope;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import org.junit.Assert;
import org.junit.Test;

import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class TracingBodyHandlerTest {

    @Test
    public void testReadBody() throws IOException {
        String traceId = "00000000000000000000000000000001";
        String spanId = "0000000000000001";
        SpanContext spanContext = SpanContext.create(traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault());
        Span span = Span.wrap(spanContext);

        try (Scope scope = span.makeCurrent()) {
            TracingBodyHandler handler = new TracingBodyHandler();
            String inputBody = "{\"foo\":\"bar\"}";
            ByteArrayInputStream inputStream = new ByteArrayInputStream(inputBody.getBytes(StandardCharsets.UTF_8));
            
            // Mock Context
            ReaderInterceptorContext context = new MockReaderInterceptorContext(inputStream);

            // Run
            Object result = handler.aroundReadFrom(context);

            // Verify result is what proceed returns (null in our mock)
            Assert.assertNull(result);

            // Verify InputStream was wrapped and read
            InputStream newIS = context.getInputStream();
            Assert.assertNotEquals(inputStream, newIS); // Should be wrapped

            // Read from the new stream to trigger logging
            byte[] buffer = new byte[1024];
            int len = newIS.read(buffer);
            String readContent = new String(buffer, 0, len, StandardCharsets.UTF_8);
            
            Assert.assertEquals(inputBody, readContent);
        }
    }

    @Test
    public void testWriteBody() throws IOException {
        String traceId = "00000000000000000000000000000001";
        String spanId = "0000000000000001";
        SpanContext spanContext = SpanContext.create(traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault());
        Span span = Span.wrap(spanContext);
        
        try (Scope scope = span.makeCurrent()) {
            TracingBodyHandler handler = new TracingBodyHandler();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            // Mock Context
            WriterInterceptorContext context = new MockWriterInterceptorContext(outputStream);

            // Run
            handler.aroundWriteTo(context);

            // Verify Output Stream contains what was written
            String output = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
            Assert.assertEquals("response-data", output);
            
            // Verify logic execution by confirming logs would be written (implied)
        }
    }
    
    // Minimal Mocks
    
    private static class MockReaderInterceptorContext implements ReaderInterceptorContext {
        private InputStream is;
        
        public MockReaderInterceptorContext(InputStream is) {
            this.is = is;
        }

        @Override
        public Object proceed() throws IOException {
             return null;
        }

        @Override public InputStream getInputStream() { return is; }
        @Override public void setInputStream(InputStream is) { this.is = is; }
        @Override public MediaType getMediaType() { return MediaType.APPLICATION_JSON_TYPE; }
        @Override public void setMediaType(MediaType mediaType) {}

        // Unused
        @Override public Object getProperty(String name) { return null; }
        @Override public java.util.Collection<String> getPropertyNames() { return null; }
        @Override public void setProperty(String name, Object object) {}
        @Override public void removeProperty(String name) {}
        @Override public java.lang.annotation.Annotation[] getAnnotations() { return new java.lang.annotation.Annotation[0]; }
        @Override public void setAnnotations(java.lang.annotation.Annotation[] annotations) {}
        @Override public Class<?> getType() { return null; }
        @Override public void setType(Class<?> type) {}
        @Override public java.lang.reflect.Type getGenericType() { return null; }
        @Override public void setGenericType(java.lang.reflect.Type genericType) {}
        @Override public MultivaluedMap getHeaders() { return null; }
    }

    private static class MockWriterInterceptorContext implements WriterInterceptorContext {
        private OutputStream os;

        public MockWriterInterceptorContext(OutputStream os) {
            this.os = os;
        }

        @Override
        public void proceed() throws IOException {
            os.write("response-data".getBytes(StandardCharsets.UTF_8));
        }

        @Override public OutputStream getOutputStream() { return os; }
        @Override public void setOutputStream(OutputStream os) { this.os = os; }
        @Override public MediaType getMediaType() { return MediaType.APPLICATION_JSON_TYPE; }
        @Override public void setMediaType(MediaType mediaType) {}
        
        // Unused
        @Override public Object getEntity() { return null; }
        @Override public void setEntity(Object entity) {}
        @Override public Object getProperty(String name) { return null; }
        @Override public java.util.Collection<String> getPropertyNames() { return null; }
        @Override public void setProperty(String name, Object object) {}
        @Override public void removeProperty(String name) {}
        @Override public java.lang.annotation.Annotation[] getAnnotations() { return new java.lang.annotation.Annotation[0]; }
        @Override public void setAnnotations(java.lang.annotation.Annotation[] annotations) {}
        @Override public Class<?> getType() { return null; }
        @Override public void setType(Class<?> type) {}
        @Override public java.lang.reflect.Type getGenericType() { return null; }
        @Override public void setGenericType(java.lang.reflect.Type genericType) {}
        @Override public MultivaluedMap getHeaders() { return null; }
    }
}
