import "server-only";
import { NextResponse } from "next/server";
import { ApiRequestError } from "./client";

/**
 * Runs a backend call from a Route Handler and turns the result into a
 * NextResponse — success responses keep the backend's real status/body,
 * ApiRequestError (backend rejection or network failure) becomes a JSON
 * response carrying the same ApiError-shaped payload, so the browser-side
 * fetch and mapApiError.ts see one consistent shape either way.
 */
export async function relay<T>(
  run: () => Promise<{ status: number; data: T }>,
): Promise<NextResponse> {
  try {
    const { status, data } = await run();
    return NextResponse.json(data ?? {}, { status });
  } catch (error) {
    if (error instanceof ApiRequestError) {
      return NextResponse.json(error.payload, { status: error.payload.status || 502 });
    }
    throw error;
  }
}
