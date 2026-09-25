import type { ReactElement } from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { TicketList } from "@/components/tickets/TicketList";
import * as ticketsApi from "@/lib/api/tickets";
import type { Ticket } from "@/types/ticket";
import type { TicketPage } from "@/types/page";

vi.mock("@/lib/api/tickets", () => ({
  listTickets: vi.fn(),
}));

let observerCallback: IntersectionObserverCallback | null = null;

class MockIntersectionObserver implements IntersectionObserver {
  readonly root = null;
  readonly rootMargin = "";
  readonly thresholds: ReadonlyArray<number> = [];
  constructor(callback: IntersectionObserverCallback) {
    observerCallback = callback;
  }
  observe = vi.fn();
  unobserve = vi.fn();
  disconnect = vi.fn();
  takeRecords = () => [];
}

function renderWithClient(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

function makeTicket(overrides: Partial<Ticket> = {}): Ticket {
  return {
    id: "1",
    title: "Sample",
    description: "desc",
    priority: "LOW",
    assignee: "jane.doe",
    status: "OPEN",
    category: null,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

beforeEach(() => {
  vi.stubGlobal("IntersectionObserver", MockIntersectionObserver);
  observerCallback = null;
  vi.mocked(ticketsApi.listTickets).mockReset();
});

describe("TicketList", () => {
  it("renders 'no tickets found' when totalElements is 0", async () => {
    vi.mocked(ticketsApi.listTickets).mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    } satisfies TicketPage<Ticket>);

    renderWithClient(<TicketList q="" status={undefined} />);

    expect(await screen.findByText(/no tickets found/i)).toBeInTheDocument();
  });

  it("fetches the next page when the scroll sentinel intersects and more pages remain", async () => {
    vi.mocked(ticketsApi.listTickets).mockImplementation(async ({ page = 0 } = {}) => ({
      content: [makeTicket({ id: String(page), title: `Ticket ${page}` })],
      page,
      size: 1,
      totalElements: 2,
      totalPages: 2,
    }));

    renderWithClient(<TicketList q="" status={undefined} />);

    await screen.findByText("Ticket 0");
    await waitFor(() => expect(observerCallback).not.toBeNull());
    expect(ticketsApi.listTickets).toHaveBeenCalledWith(expect.objectContaining({ page: 0 }));

    observerCallback?.(
      [{ isIntersecting: true } as IntersectionObserverEntry],
      {} as IntersectionObserver,
    );

    await waitFor(() => expect(ticketsApi.listTickets).toHaveBeenCalledTimes(2));
    expect(ticketsApi.listTickets).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }));
  });

  it("does not fetch further pages once the last page has been loaded", async () => {
    vi.mocked(ticketsApi.listTickets).mockResolvedValue({
      content: [makeTicket()],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });

    renderWithClient(<TicketList q="" status={undefined} />);

    await screen.findByText("Sample");
    await waitFor(() => expect(observerCallback).not.toBeNull());

    observerCallback?.(
      [{ isIntersecting: true } as IntersectionObserverEntry],
      {} as IntersectionObserver,
    );

    await new Promise((resolve) => setTimeout(resolve, 10));
    expect(ticketsApi.listTickets).toHaveBeenCalledTimes(1);
  });
});
