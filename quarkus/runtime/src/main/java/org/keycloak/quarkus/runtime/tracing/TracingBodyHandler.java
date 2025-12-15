package org.keycloak.quarkus.runtime.tracing;

import io.opentelemetry.api.trace.Span;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;

@Provider
@Priority(Priorities.USER)
public class TracingBodyHandler implements ContainerRequestFilter, ContainerResponseFilter, ReaderInterceptor, WriterInterceptor {

    private static final Logger log = Logger.getLogger(TracingBodyHandler.class);
    private static final int MAX_BODY_SIZE = -1; // Unlimited

    @Override
    public void filter(ContainerRequestContext context) throws IOException {
        Span span = Span.current();
        if (span == null || !span.getSpanContext().isValid()) {
            return;
        }

        String method = context.getMethod();
        String uri = context.getUriInfo().getRequestUri().toString();
        log.infof("TracingBodyHandler: capturing request details. Method: %s, URI: %s", method, uri);

        // Capture Method
        span.setAttribute("http.request.method", method);

        // Capture URI
        span.setAttribute("http.request.uri", uri);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        Span span = Span.current();
        if (span == null || !span.getSpanContext().isValid()) {
            return;
        }

        // Capture Query Parameters safely after matching
        MultivaluedMap<String, String> queryParams = requestContext.getUriInfo().getQueryParameters();
        if (queryParams != null && !queryParams.isEmpty()) {
            log.infof("TracingBodyHandler: capturing query params: %s", queryParams);
            span.setAttribute("http.request.query_params", queryParams.toString());
        }

        // Capture Path Parameters safely after matching
        MultivaluedMap<String, String> pathParams = requestContext.getUriInfo().getPathParameters();
        if (pathParams != null && !pathParams.isEmpty()) {
            log.infof("TracingBodyHandler: capturing path params: %s", pathParams);
            span.setAttribute("http.request.path_params", pathParams.toString());
        }
    }

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException, WebApplicationException {
        Span span = Span.current();
        if (span == null || !span.getSpanContext().isValid()) {
            return context.proceed();
        }

        MediaType mediaType = context.getMediaType();
        if (mediaType != null && !mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
            return context.proceed();
        }

        InputStream originalStream = context.getInputStream();
        if (originalStream == null) {
            return context.proceed();
        }

        // Wrap input stream to record as we read
        TracingInputStream wrappedStream = new TracingInputStream(originalStream, span);
        context.setInputStream(wrappedStream);

        try {
            return context.proceed();
        } catch (Exception e) {
            // Ensure we log what we have so far if something fails
            wrappedStream.logBody();
            throw e;
        }
    }

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
        Span span = Span.current();
        if (span == null || !span.getSpanContext().isValid()) {
            context.proceed();
            return;
        }

        MediaType mediaType = context.getMediaType();
        if (mediaType != null && !mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
            context.proceed();
            return;
        }

        OutputStream originalStream = context.getOutputStream();
        TracingOutputStream wrappedStream = new TracingOutputStream(originalStream, span);

        context.setOutputStream(wrappedStream);
        try {
            context.proceed();
        } finally {
            wrappedStream.logBody();
        }
    }

    private static class TracingInputStream extends InputStream {
        private final InputStream delegate;
        private final ByteArrayOutputStream buffer;
        private final Span span;
        private boolean logged = false;

        public TracingInputStream(InputStream delegate, Span span) {
            this.delegate = delegate;
            this.span = span;
            this.buffer = new ByteArrayOutputStream();
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                buffer.write(b);
            } else {
                logBody();
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = delegate.read(b, off, len);
            if (read != -1) {
                buffer.write(b, off, read);
            } else {
                logBody();
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            logBody();
            delegate.close();
        }

        private void logBody() {
            if (logged) return;
            
            byte[] data = buffer.toByteArray();
            if (data.length > 0) {
                String body = new String(data, StandardCharsets.UTF_8);
                log.infof("TracingBodyHandler: capturing request body (len=%d): %s", data.length, body);
                span.setAttribute("http.request.body", body);
            }
            logged = true;
        }
    }

    private static class TracingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final ByteArrayOutputStream buffer;
        private final Span span;
        private boolean logged = false;

        public TracingOutputStream(OutputStream delegate, Span span) {
            this.delegate = delegate;
            this.span = span;
            this.buffer = new ByteArrayOutputStream();
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            buffer.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            buffer.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        public void logBody() {
             if (logged) return;

            byte[] data = buffer.toByteArray();
            if (data.length > 0) {
                String body = new String(data, StandardCharsets.UTF_8);
                log.infof("TracingBodyHandler: capturing response body (len=%d): %s", data.length, body);
                span.setAttribute("http.response.body", body);
            }
            logged = true;
        }
    }
}

