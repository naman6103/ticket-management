import type { MappedMessage } from "@/lib/errors/mapApiError";

interface ErrorDisplayProps {
  messages: MappedMessage[];
}

/**
 * The single component every screen uses to render backend-driven
 * error/empty states (FR-012, FR-013). Never bypassed with a bespoke
 * per-screen error UI — see architecture.md §Error mapping.
 */
export function ErrorDisplay({ messages }: ErrorDisplayProps) {
  if (messages.length === 0) {
    return null;
  }

  return (
    <>
      {messages.map((message, index) => {
        if (message.kind === "fullpage") {
          return (
            <div key={index} role="alert" data-testid="error-fullpage" className="errorFullpage">
              <p>{message.text}</p>
            </div>
          );
        }
        if (message.kind === "field") {
          return (
            <p
              key={index}
              role="alert"
              data-testid={`error-field-${message.fieldName}`}
              data-field={message.fieldName}
              className="errorField"
            >
              {message.text}
            </p>
          );
        }
        return (
          <p key={index} role="alert" data-testid="error-banner" className="errorBanner">
            {message.text}
          </p>
        );
      })}
    </>
  );
}
