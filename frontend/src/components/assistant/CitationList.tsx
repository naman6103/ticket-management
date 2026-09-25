import Link from "next/link";

interface CitationListProps {
  ticketIds: string[];
}

/**
 * Renders cited ticket IDs as a list separate from the answer prose
 * (spec.md Clarification #1) — never woven inline into `AiAnswer`'s text.
 * Each link opens the ticket's detail view in a new tab, leaving this
 * panel open and unaffected (spec Clarification #2, FR-005).
 */
export function CitationList({ ticketIds }: CitationListProps) {
  return (
    <ul className="citationList">
      {ticketIds.map((ticketId) => (
        <li key={ticketId} data-testid="citation-item">
          <Link href={`/tickets/${ticketId}`} target="_blank" rel="noopener noreferrer">
            {ticketId}
          </Link>
        </li>
      ))}
    </ul>
  );
}
