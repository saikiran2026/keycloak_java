/**
 * W3C Trace Context - Global fetch interceptor
 * 
 * Patches the global fetch function to automatically inject traceparent headers.
 * All requests within a browser session share the same trace ID, allowing
 * traces to be grouped together in Jaeger.
 */

const TRACE_ID_KEY = 'keycloak_trace_id';
const TRACE_VERSION = '00';
const TRACE_FLAGS_SAMPLED = '01';

let originalFetch: typeof fetch;
let isPatched = false;

/**
 * Generates a random hex string of the specified length
 */
function generateHex(length: number): string {
  const bytes = new Uint8Array(length / 2);
  crypto.getRandomValues(bytes);
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

/**
 * Gets or creates a trace ID for the current session
 * The trace ID persists across page reloads within the same browser session
 */
function getOrCreateTraceId(): string {
  let traceId = sessionStorage.getItem(TRACE_ID_KEY);
  
  if (!traceId) {
    // Generate a new 128-bit (32 hex chars) trace ID
    traceId = generateHex(32);
    sessionStorage.setItem(TRACE_ID_KEY, traceId);
  }
  
  return traceId;
}

/**
 * Generates a new parent span ID for each request
 * This creates a new span while maintaining the same trace ID
 */
function generateParentId(): string {
  // Generate a new 64-bit (16 hex chars) parent ID for each request
  return generateHex(16);
}

/**
 * Gets the current traceparent header value
 * Format: version-trace-id-parent-id-trace-flags
 */
function getTraceparentHeader(): string {
  const traceId = getOrCreateTraceId();
  const parentId = generateParentId();
  
  return `${TRACE_VERSION}-${traceId}-${parentId}-${TRACE_FLAGS_SAMPLED}`;
}

/**
 * Patches the global fetch function to inject traceparent headers
 */
export function patchFetchForTracing(): void {
  if (isPatched || typeof window === 'undefined') {
    return;
  }

  originalFetch = window.fetch;
  
  window.fetch = async function(
    input: RequestInfo | URL,
    init?: RequestInit
  ): Promise<Response> {
    // Only add traceparent if not already present
    const headers = new Headers(init?.headers);
    
    if (!headers.has('traceparent')) {
      try {
        headers.set('traceparent', getTraceparentHeader());
      } catch (error) {
        // If trace context generation fails, continue without it
        console.warn('Failed to generate trace context:', error);
      }
    }

    return originalFetch(input, {
      ...init,
      headers,
    });
  };

  isPatched = true;
}

/**
 * Resets the trace ID (creates a new trace for a new session)
 */
export function resetTraceContext(): void {
  sessionStorage.removeItem(TRACE_ID_KEY);
}

/**
 * Gets the current trace ID (for debugging/logging)
 */
export function getCurrentTraceId(): string {
  return getOrCreateTraceId();
}

