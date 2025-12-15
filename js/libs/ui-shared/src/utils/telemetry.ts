
const SESSION_ID_KEY = 'keycloak_session_id';
let originalFetch: typeof fetch;
let isPatched = false;

function generateHex(length: number): string {
  const bytes = new Uint8Array(length / 2);
  crypto.getRandomValues(bytes);
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

function getOrCreateSessionId(): string {
  let sessionId = sessionStorage.getItem(SESSION_ID_KEY);

  if (!sessionId) {
    // Generate a new 64-bit (16 hex chars) session ID
    sessionId = generateHex(16);
    sessionStorage.setItem(SESSION_ID_KEY, sessionId);
  }

  return sessionId;
}

function getBaggageHeader(): string {
  const sessionId = getOrCreateSessionId();
  return `session.id=${sessionId}`;
}

export function patchFetchForTelemetry(): void {
  if (isPatched || typeof window === 'undefined') {
    return;
  }

  originalFetch = window.fetch;

  window.fetch = async function (
    input: RequestInfo | URL,
    init?: RequestInit
  ): Promise<Response> {
    // Only add baggage if not already present
    const headers = new Headers(init?.headers);

    if (!headers.has('baggage')) {
      try {
        headers.set('baggage', getBaggageHeader());
      } catch (error) {
        // If telemetry generation fails, continue without it
        console.warn('Failed to generate telemetry context:', error);
      }
    }

    return originalFetch(input, {
      ...init,
      headers,
    });
  };

  isPatched = true;
}

export function resetTelemetryContext(): void {
  sessionStorage.removeItem(SESSION_ID_KEY);
}

export function getCurrentSessionId(): string {
  return getOrCreateSessionId();
}
