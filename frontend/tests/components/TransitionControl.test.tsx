import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { TransitionControl } from "@/components/tickets/TransitionControl";
import { BrowserApiError } from "@/lib/api/browserFetch";
import * as ticketsApi from "@/lib/api/tickets";
import type { Ticket } from "@/types/ticket";

vi.mock("@/lib/api/tickets", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/tickets")>();
  return { ...actual, transitionTicket: vi.fn() };
});

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
    comments: [{ id: "c1", ticketId: "t1", content: "existing", createdAt: "2026-01-01T00:00:00Z" }],
    ...overrides,
  };
}

function renderControl(ticket: Ticket) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(["ticket", ticket.id], ticket);
  render(
    <QueryClientProvider client={queryClient}>
      <TransitionControl ticketId={ticket.id} currentStatus={ticket.status} />
    </QueryClientProvider>,
  );
  return queryClient;
}

beforeEach(() => {
  vi.mocked(ticketsApi.transitionTicket).mockReset();
});

describe("TransitionControl", () => {
  it("offers only the lookup table's next statuses for OPEN", () => {
    renderControl(makeTicket({ status: "OPEN" }));

    const select = screen.getByLabelText("Transition status");
    const optionValues = Array.from(select.querySelectorAll("option")).map((o) => o.textContent);
    expect(optionValues).toEqual(["Select next status", "IN_PROGRESS", "CANCELLED"]);
  });

  it("renders nothing for a terminal status (CLOSED)", () => {
    renderControl(makeTicket({ status: "CLOSED" }));

    expect(screen.queryByLabelText("Transition status")).not.toBeInTheDocument();
  });

  it("shows the backend's 409 INVALID_TRANSITION message and resets selection", async () => {
    vi.mocked(ticketsApi.transitionTicket).mockRejectedValue(
      new BrowserApiError({
        timestamp: "2026-01-01T00:00:00Z",
        status: 409,
        error: "Conflict",
        code: "INVALID_TRANSITION",
        message: "Cannot transition ticket from OPEN to CLOSED",
        path: "/api/v1/tickets/t1/transitions",
        details: [],
      }),
    );
    renderControl(makeTicket({ status: "OPEN" }));

    await userEvent.selectOptions(screen.getByLabelText("Transition status"), "IN_PROGRESS");
    await userEvent.click(screen.getByRole("button", { name: /confirm/i }));

    expect(
      await screen.findByText("Cannot transition ticket from OPEN to CLOSED"),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Transition status")).toHaveValue("");
  });

  it("preserves the ticket's comments in the cache after a successful transition (backend response omits them)", async () => {
    const ticket = makeTicket({ status: "OPEN" });
    vi.mocked(ticketsApi.transitionTicket).mockResolvedValue({
      ...ticket,
      status: "IN_PROGRESS",
      comments: undefined,
    });
    const queryClient = renderControl(ticket);

    await userEvent.selectOptions(screen.getByLabelText("Transition status"), "IN_PROGRESS");
    await userEvent.click(screen.getByRole("button", { name: /confirm/i }));

    await waitFor(() => {
      const updated = queryClient.getQueryData<Ticket>(["ticket", "t1"]);
      expect(updated?.status).toBe("IN_PROGRESS");
      expect(updated?.comments).toEqual(ticket.comments);
    });
  });
});
