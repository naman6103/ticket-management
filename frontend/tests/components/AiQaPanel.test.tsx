import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AiQaPanel } from "@/components/assistant/AiQaPanel";
import { NoRelevantTicketsState } from "@/components/assistant/NoRelevantTicketsState";
import { ErrorDisplay } from "@/components/errors/ErrorDisplay";
import { BrowserApiError } from "@/lib/api/browserFetch";
import * as assistantApi from "@/lib/api/assistant";

vi.mock("@/lib/api/assistant", () => ({ askAssistant: vi.fn() }));

beforeEach(() => {
  vi.mocked(assistantApi.askAssistant).mockReset();
});

async function ask(question: string) {
  await userEvent.type(screen.getByLabelText("Ask a question"), question);
  await userEvent.click(screen.getByRole("button", { name: /ask/i }));
}

describe("AiQaPanel", () => {
  it("renders the answer text and one citation per ticketId, in a list separate from the answer paragraph", async () => {
    vi.mocked(assistantApi.askAssistant).mockResolvedValue({
      answer: "Yes, we've seen this before.",
      ticketIds: ["ticket-1", "ticket-2"],
      noRelevantTicketsFound: false,
    });
    render(<AiQaPanel />);

    await ask("have we seen this before?");

    expect(await screen.findByText("Yes, we've seen this before.")).toBeInTheDocument();
    const citations = screen.getAllByTestId("citation-item");
    expect(citations).toHaveLength(2);
    expect(citations[0]).toHaveTextContent("ticket-1");
    expect(citations[1]).toHaveTextContent("ticket-2");
  });

  it("disables the textarea and submit button while the request is pending, and re-enables on resolve", async () => {
    let resolvePromise!: (value: {
      answer: string;
      ticketIds: string[];
      noRelevantTicketsFound: boolean;
    }) => void;
    vi.mocked(assistantApi.askAssistant).mockReturnValue(
      new Promise((resolve) => {
        resolvePromise = resolve;
      }),
    );
    render(<AiQaPanel />);

    await userEvent.type(screen.getByLabelText("Ask a question"), "a pending question");
    await userEvent.click(screen.getByRole("button", { name: /ask/i }));

    expect(screen.getByLabelText("Ask a question")).toBeDisabled();
    expect(screen.getByRole("button", { name: /ask/i })).toBeDisabled();
    expect(screen.getByRole("status")).toHaveTextContent(/asking the assistant/i);

    resolvePromise({ answer: "Done.", ticketIds: [], noRelevantTicketsFound: false });

    await waitFor(() => {
      expect(screen.getByLabelText("Ask a question")).not.toBeDisabled();
    });
    expect(screen.getByRole("button", { name: /ask/i })).not.toBeDisabled();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("replaces the previous answer/citations entirely when a new question is submitted", async () => {
    vi.mocked(assistantApi.askAssistant).mockResolvedValueOnce({
      answer: "First answer.",
      ticketIds: ["ticket-1"],
      noRelevantTicketsFound: false,
    });
    render(<AiQaPanel />);

    await ask("first question");
    expect(await screen.findByText("First answer.")).toBeInTheDocument();
    expect(screen.getAllByTestId("citation-item")).toHaveLength(1);

    vi.mocked(assistantApi.askAssistant).mockResolvedValueOnce({
      answer: "Second answer.",
      ticketIds: ["ticket-2", "ticket-3"],
      noRelevantTicketsFound: false,
    });
    await userEvent.clear(screen.getByLabelText("Ask a question"));
    await ask("second question");

    expect(await screen.findByText("Second answer.")).toBeInTheDocument();
    expect(screen.queryByText("First answer.")).not.toBeInTheDocument();
    const citations = screen.getAllByTestId("citation-item");
    expect(citations).toHaveLength(2);
    expect(citations[0]).toHaveTextContent("ticket-2");
    expect(citations[1]).toHaveTextContent("ticket-3");
  });

  it("renders the no-match state with zero citations when the assistant finds nothing relevant", async () => {
    vi.mocked(assistantApi.askAssistant).mockResolvedValue({
      answer: "No relevant tickets found for this question.",
      ticketIds: [],
      noRelevantTicketsFound: true,
    });
    render(<AiQaPanel />);

    await ask("something totally unrelated");

    expect(await screen.findByTestId("ai-no-match")).toBeInTheDocument();
    expect(screen.queryByTestId("citation-item")).not.toBeInTheDocument();
    expect(screen.queryByTestId("ai-answer")).not.toBeInTheDocument();
  });

  it("renders a field-level error under the textarea for a 400 VALIDATION_FAILED response with details", async () => {
    vi.mocked(assistantApi.askAssistant).mockRejectedValue(
      new BrowserApiError({
        timestamp: "2026-01-01T00:00:00Z",
        status: 400,
        error: "Bad Request",
        code: "VALIDATION_FAILED",
        message: "Request validation failed",
        path: "/api/ai/ask",
        details: [{ field: "question", rejectedValue: "", message: "must not be blank" }],
      }),
    );
    render(<AiQaPanel />);

    await ask("a question the backend rejects anyway");

    expect(await screen.findByTestId("error-field-question")).toHaveTextContent(
      /must not be blank/i,
    );
  });

  it("renders the shared error banner for a 502 AI_GENERATION_FAILED response", async () => {
    vi.mocked(assistantApi.askAssistant).mockRejectedValue(
      new BrowserApiError({
        timestamp: "2026-01-01T00:00:00Z",
        status: 502,
        error: "Bad Gateway",
        code: "AI_GENERATION_FAILED",
        message: "The assistant couldn't generate an answer.",
        path: "/api/ai/ask",
        details: [],
      }),
    );
    render(<AiQaPanel />);

    await ask("a question that fails generation");

    expect(await screen.findByTestId("error-banner")).toHaveTextContent(
      /couldn't generate an answer/i,
    );
  });

  it("renders the shared error banner for a 503 AI_RETRIEVAL_UNAVAILABLE response", async () => {
    vi.mocked(assistantApi.askAssistant).mockRejectedValue(
      new BrowserApiError({
        timestamp: "2026-01-01T00:00:00Z",
        status: 503,
        error: "Service Unavailable",
        code: "AI_RETRIEVAL_UNAVAILABLE",
        message: "The assistant is unavailable.",
        path: "/api/ai/ask",
        details: [],
      }),
    );
    render(<AiQaPanel />);

    await ask("a question that fails retrieval");

    expect(await screen.findByTestId("error-banner")).toHaveTextContent(/temporarily unavailable/i);
  });
});

describe("no-match vs error presentation (FR-007, SC-003)", () => {
  it("renders the no-match block and a failure block with different data-testids/markup", () => {
    const { unmount } = render(<NoRelevantTicketsState />);
    const noMatchTestId = screen.getByTestId("ai-no-match").getAttribute("data-testid");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    unmount();

    render(<ErrorDisplay messages={[{ kind: "banner", text: "The assistant is unavailable." }]} />);
    const errorTestId = screen.getByTestId("error-banner").getAttribute("data-testid");
    expect(screen.getByRole("alert")).toBeInTheDocument();

    expect(noMatchTestId).not.toEqual(errorTestId);
  });
});
