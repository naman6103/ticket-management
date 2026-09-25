import { CitationList } from "./CitationList";

interface AiAnswerProps {
  answer: string;
  ticketIds: string[];
}

/** Renders the assistant's grounded answer text, followed by its citations as a separate list. */
export function AiAnswer({ answer, ticketIds }: AiAnswerProps) {
  return (
    <div className="aiAnswer" data-testid="ai-answer">
      <p>{answer}</p>
      <CitationList ticketIds={ticketIds} />
    </div>
  );
}
