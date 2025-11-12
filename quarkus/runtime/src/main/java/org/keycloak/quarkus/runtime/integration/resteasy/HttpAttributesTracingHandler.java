/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.quarkus.runtime.integration.resteasy;

import io.opentelemetry.api.trace.Span;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.core.ResteasyReactiveRequestContext;
import org.jboss.resteasy.reactive.server.core.multipart.FormData;
import org.jboss.resteasy.reactive.server.multipart.FormValue;
import org.jboss.resteasy.reactive.server.spi.ServerRestHandler;
import org.keycloak.tracing.TracingAttributes;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handler that captures HTTP request attributes and adds them to the active OpenTelemetry span.
 * This includes request body, query parameters, headers, method, and path.
 *
 * The handler runs in the BEFORE_METHOD_INVOKE phase after the span has been created
 * by KeycloakTracingCustomizer, allowing it to enhance the span with detailed HTTP metadata.
 */
public final class HttpAttributesTracingHandler implements ServerRestHandler {

    private static final Logger logger = Logger.getLogger(HttpAttributesTracingHandler.class);

    @Override
    public void handle(ResteasyReactiveRequestContext requestContext) {
        // Retrieve the span created by KeycloakTracingCustomizer
        Span span = (Span) requestContext.getProperty("span");

        if (span == null) {
            // No active span, nothing to enhance
            return;
        }

        try {
            // Add HTTP method
            String method = requestContext.getMethod();
            if (method != null) {
                span.setAttribute(TracingAttributes.HTTP_REQUEST_METHOD, method);
            }

            // Add request path and query parameters
            UriInfo uriInfo = requestContext.getUriInfo();
            if (uriInfo != null) {
                span.setAttribute(TracingAttributes.HTTP_REQUEST_PATH, uriInfo.getPath());

                MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
                if (queryParams != null && !queryParams.isEmpty()) {
                    span.setAttribute(TracingAttributes.HTTP_REQUEST_QUERY, queryParams.toString());
                }
            }

            // Add request headers
            HttpHeaders httpHeaders = requestContext.getHttpHeaders();
            if (httpHeaders != null) {
                MultivaluedMap<String, String> headers = httpHeaders.getRequestHeaders();
                if (headers != null && !headers.isEmpty()) {
                    StringBuilder headersBuilder = new StringBuilder();
                    headers.forEach((key, values) -> {
                        if (headersBuilder.length() > 0) {
                            headersBuilder.append(", ");
                        }
                        headersBuilder.append(key).append(": ").append(String.join(";", values));
                    });
                    span.setAttribute(TracingAttributes.HTTP_REQUEST_HEADERS, headersBuilder.toString());
                }
            }

            // Add request body if present
            captureRequestBody(requestContext, span);

        } catch (Exception e) {
            // Log error but don't fail the request
            logger.warn("Failed to capture HTTP attributes for tracing", e);
        }
    }

    /**
     * Captures the request body and adds it as a span attribute.
     * This method attempts to read the body from form data or routing context.
     */
    private void captureRequestBody(ResteasyReactiveRequestContext requestContext, Span span) {
        try {
            // Try to get the body from form data if it's already been parsed
            FormData formData = requestContext.getFormData();
            if (formData != null && formData.iterator().hasNext()) {
                StringBuilder formBuilder = new StringBuilder();
                for (String name : formData) {
                    Deque<FormValue> values = formData.get(name);
                    if (values != null) {
                        for (FormValue value : values) {
                            if (formBuilder.length() > 0) {
                                formBuilder.append("&");
                            }
                            formBuilder.append(name).append("=").append(value.getValue());
                        }
                    }
                }
                if (formBuilder.length() > 0) {
                    String body = formBuilder.toString();
                    logger.infof("Captured form body: length=%d", body.length());
                    span.setAttribute(TracingAttributes.HTTP_REQUEST_BODY, body);
                    span.setAttribute(TracingAttributes.HTTP_REQUEST_BODY_SIZE, (long) body.length());
                    return;
                }
            }

            // For JSON or other content types, try to read from routing context body
            try {
                Instance<RoutingContext> instances = CDI.current().select(RoutingContext.class);
                if (instances.isResolvable()) {
                    RoutingContext routingContext = instances.get();
                    io.vertx.core.buffer.Buffer bodyBuffer = routingContext.body().buffer();

                    if (bodyBuffer != null && bodyBuffer.length() > 0) {
                        String body = bodyBuffer.toString(StandardCharsets.UTF_8);
                        logger.infof("Captured routing context body: length=%d", body.length());

                        // Always set both attributes for consistency
                        span.setAttribute(TracingAttributes.HTTP_REQUEST_BODY_SIZE, (long) body.length());

                        // Limit body size to avoid overwhelming spans (10KB limit)
                        if (body.length() > 10240) {
                            String truncatedBody = body.substring(0, 10240) + "... [truncated]";
                            span.setAttribute(TracingAttributes.HTTP_REQUEST_BODY, truncatedBody);
                            logger.infof("Body truncated from %d to %d chars", body.length(), truncatedBody.length());
                        } else {
                            span.setAttribute(TracingAttributes.HTTP_REQUEST_BODY, body);
                        }
                    } else {
                        logger.debug("Body buffer is null or empty");
                    }
                } else {
                    logger.debug("RoutingContext not resolvable");
                }
            } catch (Exception e) {
                logger.warn("Failed to access routing context body", e);
            }
        } catch (Exception e) {
            logger.warn("Failed to capture request body for tracing", e);
        }
    }
}
