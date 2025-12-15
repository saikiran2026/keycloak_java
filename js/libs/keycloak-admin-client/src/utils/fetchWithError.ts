const ERROR_FIELDS = ["error", "errorMessage"];

export type NetworkErrorOptions = { response: Response; responseData: unknown };

export class NetworkError extends Error {
  response: Response;
  responseData: unknown;

  constructor(message: string, options: NetworkErrorOptions) {
    super(message);
    this.response = options.response;
    this.responseData = options.responseData;
  }
}

// Global variable to store the latest traceparent from the backend
let currentTraceparent: string | null = null;

export async function fetchWithError(
  input: Request | string | URL,
  init?: RequestInit,
) {
  // 1. Inject traceparent header if available
  if (currentTraceparent) {
    if (!init) {
      init = {};
    }
    if (!init.headers) {
      init.headers = {};
    }

    if (init.headers instanceof Headers) {
      init.headers.set("traceparent", currentTraceparent);
    } else if (Array.isArray(init.headers)) {
      init.headers.push(["traceparent", currentTraceparent]);
    } else {
      (init.headers as Record<string, string>)["traceparent"] = currentTraceparent;
    }
  }

  const response = await fetch(input, init);

  // 2. Capture new traceparent header from response
  const newTraceparent = response.headers.get("traceparent");
  if (newTraceparent) {
    currentTraceparent = newTraceparent;
  }

  if (!response.ok) {
    const responseData = await parseResponse(response);
    const message = getErrorMessage(responseData);
    throw new NetworkError(message, {
      response,
      responseData,
    });
  }

  return response;
}

export async function parseResponse(response: Response): Promise<any> {
  if (!response.body) {
    return "";
  }

  const data = await response.text();

  try {
    return JSON.parse(data);
  } catch {
    return data;
  }
}

function getErrorMessage(data: unknown): string {
  if (typeof data !== "object" || data === null) {
    return "Unable to determine error message.";
  }

  for (const key of ERROR_FIELDS) {
    const value = (data as Record<string, unknown>)[key];

    if (typeof value === "string") {
      return value;
    }
  }

  return "Network response was not OK.";
}
