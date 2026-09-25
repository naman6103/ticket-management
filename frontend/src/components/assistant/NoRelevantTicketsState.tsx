/**
 * Dedicated no-match state — deliberately NOT built on `ErrorDisplay`/
 * `mapApiError.ts` (research.md §3). `noRelevantTicketsFound: true` is a
 * `200 OK`, never an `ApiError`, so it must never look identical to a real
 * request failure (spec.md FR-007).
 */
export function NoRelevantTicketsState() {
  return (
    <div className="aiNoMatch" data-testid="ai-no-match">
      <p>No relevant tickets found for this question.</p>
    </div>
  );
}
