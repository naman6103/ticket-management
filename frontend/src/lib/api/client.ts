import "server-only";
import type { ApiError } from "@/types/apiError";

/**
 * Server-side-only HTTP client for talking to the backend. Only ever
 * imported from Route Handlers (frontend/src/app/api/**) — never from
 * client components — because API_BASE_URL uses the Docker Compose service
 * name (e.g. http://app:8080), which a browser cannot resolve.
 */

export class ApiRequestError extends Error {
  constructor(
    message: string,
    public readonly payload: ApiError,
  ) {
    super(message);
    this.name = "ApiRequestError";
  }
}

function getApiBaseUrl(): string {
  const baseUrl = process.env.API_BASE_URL;
  if (!baseUrl) {
    throw new Error(
      "API_BASE_URL is not set. Configure it in the environment (see .env.example).",
    );
  }
  return baseUrl;
}

function networkErrorPayload(path: string): ApiError {
  return {
    timestamp: new Date().toISOString(),
    status: 0,
    error: "Network Error",
    code: "NETWORK_ERROR",
    message: "Couldn't reach the server. Check your connection and try again.",
    path,
    details: [],
  };
}

export interface ApiResponse<T> {
  status: number;
  data: T;
}

/**
 * Calls the backend and returns both the body and the real HTTP status, so
 * Route Handlers can relay the backend's exact status code (e.g. 201 on
 * create, 200 on update) instead of guessing one.
 */
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<ApiResponse<T>> {
  const url = `${getApiBaseUrl()}${path}`;

  let response: Response;
  try {
    response = await fetch(url, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        ...init?.headers,
      },
      cache: "no-store",
    });
  } catch {
    throw new ApiRequestError("Network request failed", networkErrorPayload(path));
  }

  if (response.status === 204) {
    return { status: response.status, data: undefined as T };
  }

  const body = await response.json().catch(() => null);

  if (!response.ok) {
    const payload: ApiError = body ?? networkErrorPayload(path);
    throw new ApiRequestError(payload.message ?? "Request failed", payload);
  }

  return { status: response.status, data: body as T };
}
