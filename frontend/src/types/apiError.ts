export type BackendApiErrorCode =
  | "VALIDATION_FAILED"
  | "TICKET_NOT_FOUND"
  | "INVALID_TRANSITION"
  | "UNKNOWN_FILTER"
  | "AI_GENERATION_FAILED"
  | "AI_RETRIEVAL_UNAVAILABLE";

/**
 * NETWORK_ERROR is not a backend code — it's synthesized client-side when a
 * request never reaches the backend (timeout, connection refused, DNS
 * failure), so mapApiError.ts can treat it uniformly with real ApiError
 * payloads instead of special-casing thrown non-ApiError exceptions.
 */
export type ApiErrorCode = BackendApiErrorCode | "NETWORK_ERROR";

export interface ApiErrorDetail {
  field: string;
  rejectedValue: unknown;
  message: string;
}

/** Mirrors the backend's shared ErrorResponse shape (api-contract.md). */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  code: ApiErrorCode;
  message: string;
  path: string;
  details: ApiErrorDetail[];
}

export function isApiError(value: unknown): value is ApiError {
  return (
    typeof value === "object" &&
    value !== null &&
    "code" in value &&
    "message" in value &&
    "details" in value
  );
}
