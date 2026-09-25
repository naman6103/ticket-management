import type { ApiError } from "@/types/apiError";

/**
 * Browser-side fetch wrapper for calling this app's own same-origin Route
 * Handlers (never the backend directly — see architecture.md §Deployment
 * architecture). Throws the parsed ApiError-shaped body on non-2xx so
 * mapApiError.ts has one consistent shape to translate, whether the
 * rejection originated from the backend or from a network failure the
 * Route Handler itself hit.
 */
export class BrowserApiError extends Error {
  constructor(public readonly payload: ApiError) {
    super(payload.message);
    this.name = "BrowserApiError";
  }
}

export async function browserFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...init?.headers,
    },
  });

  const body = await response.json().catch(() => null);

  if (!response.ok) {
    const payload: ApiError = body ?? {
      timestamp: new Date().toISOString(),
      status: response.status,
      error: "Error",
      code: "NETWORK_ERROR",
      message: "Couldn't reach the server. Check your connection and try again.",
      path,
      details: [],
    };
    throw new BrowserApiError(payload);
  }

  return body as T;
}
