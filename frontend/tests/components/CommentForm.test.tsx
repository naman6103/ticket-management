import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CommentForm } from "@/components/comments/CommentForm";
import { BrowserApiError } from "@/lib/api/browserFetch";
import * as commentsApi from "@/lib/api/comments";
import type { Ticket } from "@/types/ticket";

vi.mock("@/lib/api/comments", () => ({ addComment: vi.fn() }));

function makeTicket(overrides: Partial<Ticket> = {}): Ticket {
  return {
    id: "t1",
    title: "T",
    description: "D",
    priority: "LOW",
    assignee: "jane.doe",
    status: "OPEN",
    category: null,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    comments: [],
    ...overrides,
  };
}

function renderForm(ticketId = "t1") {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(["ticket", ticketId], makeTicket({ id: ticketId }));
  render(
    <QueryClientProvider client={queryClient}>
      <CommentForm ticketId={ticketId} />
    </QueryClientProvider>,
  );
  return queryClient;
}

beforeEach(() => {
  vi.mocked(commentsApi.addComment).mockReset();
});

describe("CommentForm", () => {
  it("shows the backend's 'content is required' message on a blank-content 400 and does not clear the composer", async () => {
    vi.mocked(commentsApi.addComment).mockRejectedValue(
      new BrowserApiError({
        timestamp: "2026-01-01T00:00:00Z",
        status: 400,
        error: "Bad Request",
        code: "VALIDATION_FAILED",
        message: "Request validation failed",
        path: "/api/v1/tickets/t1/comments",
        details: [{ field: "content", rejectedValue: "", message: "must not be blank" }],
      }),
    );
    renderForm();

    await userEvent.click(screen.getByRole("button", { name: /add comment/i }));

    expect(await screen.findByText(/must not be blank/i)).toBeInTheDocument();
    expect(screen.getByLabelText("Add a comment")).toHaveValue("");
  });

  it("appends the new comment into the ticket cache on success and clears the composer, without a full page reload", async () => {
    vi.mocked(commentsApi.addComment).mockResolvedValue({
      id: "c1",
      ticketId: "t1",
      content: "Hello",
      createdAt: "2026-01-02T00:00:00Z",
    });
    const queryClient = renderForm();

    await userEvent.type(screen.getByLabelText("Add a comment"), "Hello");
    await userEvent.click(screen.getByRole("button", { name: /add comment/i }));

    await waitFor(() => {
      const ticket = queryClient.getQueryData<Ticket>(["ticket", "t1"]);
      expect(ticket?.comments).toEqual([
        { id: "c1", ticketId: "t1", content: "Hello", createdAt: "2026-01-02T00:00:00Z" },
      ]);
    });
    expect(screen.getByLabelText("Add a comment")).toHaveValue("");
  });
});
