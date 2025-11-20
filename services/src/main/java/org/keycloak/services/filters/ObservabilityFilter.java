package org.keycloak.services.filters;

import org.jboss.logging.Logger;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

// OTel imports
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;

@Provider
public class ObservabilityFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger logger = Logger.getLogger(ObservabilityFilter.class);
    private static final String SESSION_ID_ATTRIBUTE = "session.id";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // 1. Extract Baggage and add to Span
        Context context = Context.current();
        Baggage baggage = Baggage.fromContext(context);
        String sessionId = baggage.getEntryValue(SESSION_ID_ATTRIBUTE);

        if (sessionId != null && !sessionId.isEmpty()) {
            Span.current().setAttribute(SESSION_ID_ATTRIBUTE, sessionId);
        }

        // 2. Log Request
        String method = requestContext.getMethod();
        String uri = requestContext.getUriInfo().getRequestUri().toString();
        String body = "empty";

        if (requestContext.hasEntity()) {
            InputStream entityStream = requestContext.getEntityStream();
            if (entityStream != null) {
                byte[] bytes = entityStream.readAllBytes();
                body = new String(bytes, StandardCharsets.UTF_8);
                // Reset stream so it can be read again by the actual resource
                requestContext.setEntityStream(new ByteArrayInputStream(bytes));
            }
        }

        logger.infof("Request: %s %s | Body: %s", method, uri, body);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        // 3. Log Response
        int status = responseContext.getStatus();
        Object entity = responseContext.getEntity();
        String body = (entity != null) ? entity.toString() : "empty";

        logger.infof("Response: %d | Body: %s", status, body);
    }
}
