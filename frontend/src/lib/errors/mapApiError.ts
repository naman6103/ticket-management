import type { ApiError } from "@/types/apiError";

export interface FieldMessage {
  kind: "field";
  fieldName: string;
  text: string;
}

export interface BannerMessage {
  kind: "banner";
  text: string;
}

export interface FullPageMessage {
  kind: "fullpage";
  text: string;
}

export type MappedMessage = FieldMessage | BannerMessage | FullPageMessage;

/**
 * Translates a backend ErrorResponse (or the synthesized NETWORK_ERROR
 * payload from client.ts/browserFetch.ts) into the UI's message model, per
 * architecture.md §Error mapping. Every screen routes through this before
 * rendering anything error-related — never a per-screen bespoke message.
 */
export function mapApiError(error: ApiError): MappedMessage[] {
  switch (error.code) {
    case "VALIDATION_FAILED":
      if (error.details.length > 0) {
        return error.details.map((detail) => ({
          kind: "field",
          fieldName: detail.field,
          text: detail.message,
        }));
      }
      return [{ kind: "banner", text: error.message }];

    case "TICKET_NOT_FOUND":
      return [{ kind: "fullpage", text: error.message }];

    case "INVALID_TRANSITION":
      return [{ kind: "banner", text: error.message }];

    case "UNKNOWN_FILTER":
      return [{ kind: "banner", text: error.message }];

    case "NETWORK_ERROR":
    default:
      return [{ kind: "banner", text: error.message }];
  }
}
