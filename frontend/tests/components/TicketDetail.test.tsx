import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import TicketDetailPage from "@/app/tickets/[id]/page";
import { BrowserApiError } from "@/lib/api/browserFetch";
import * as ticketsApi from "@/lib/api/tickets";

vi.mock("@/lib/api/tickets", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/tickets")>();
  return { ...actual, getTicket: vi.fn() };
});

function renderPage(id = "missing-id") {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <TicketDetailPage params={{ id }} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(ticketsApi.getTicket).mockReset();
});

describe("TicketDetailPage", () => {
  it("renders a 'ticket not found' state on a 404 TICKET_NOT_FOUND", async () => {
    vi.mocked(ticketsApi.getTicket).mockRejectedValue(
      new BrowserApiError({
        timestamp: "2026-01-01T00:00:00Z",
        status: 404,
        error: "Not Found",
        code: "TICKET_NOT_FOUND",
        message: "Ticket not found: missing-id",
        path: "/api/v1/tickets/missing-id",
        details: [],
      }),
    );

    renderPage();

    expect(await screen.findByText(/ticket not found/i)).toBeInTheDocument();
  });
});
