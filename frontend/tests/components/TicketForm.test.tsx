import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { TicketForm } from "@/components/tickets/TicketForm";
import { BrowserApiError } from "@/lib/api/browserFetch";
import * as ticketsApi from "@/lib/api/tickets";
import * as assigneesApi from "@/lib/api/assignees";
import type { Ticket } from "@/types/ticket";

vi.mock("@/lib/api/tickets", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/tickets")>();
  return { ...actual, createTicket: vi.fn(), updateTicket: vi.fn() };
});
vi.mock("@/lib/api/assignees", () => ({ listAssignees: vi.fn() }));

function renderCreateForm(onSuccess = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <TicketForm mode="create" onSuccess={onSuccess} />
    </QueryClientProvider>,
  );
  return { onSuccess };
}

function makeTicket(overrides: Partial<Ticket> = {}): Ticket {
  return {
    id: "t1",
    title: "Original title",
    description: "Original description",
    priority: "LOW",
    assignee: "jane.doe",
    status: "OPEN",
    category: null,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function renderEditForm(ticket: Ticket, onSuccess = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <TicketForm mode="edit" ticket={ticket} onSuccess={onSuccess} />
    </QueryClientProvider>,
  );
  return { onSuccess };
}

async function fillValidValues() {
  await userEvent.type(screen.getByLabelText("Title"), "A title");
  await userEvent.type(screen.getByLabelText("Description"), "A description");
  await userEvent.selectOptions(screen.getByLabelText("Priority"), "HIGH");
  await userEvent.type(screen.getByLabelText("Assignee"), "jane.doe");
}

beforeEach(() => {
  vi.mocked(assigneesApi.listAssignees).mockResolvedValue({ assignees: [] });
  vi.mocked(ticketsApi.createTicket).mockReset();
  vi.mocked(ticketsApi.updateTicket).mockReset();
});

describe("TicketForm", () => {
  it("shows blank-field hints before submit when required fields are empty", async () => {
    renderCreateForm();

    await userEvent.click(screen.getByRole("button", { name: /create ticket/i }));

    expect(await screen.findAllByText(/must not be blank/i)).toHaveLength(4);
    expect(ticketsApi.createTicket).not.toHaveBeenCalled();
  });

  it("renders per-field backend errors from a 400 VALIDATION_FAILED with details[]", async () => {
    vi.mocked(ticketsApi.createTicket).mockRejectedValue(
      new BrowserApiError({
        timestamp: "2026-01-01T00:00:00Z",
        status: 400,
        error: "Bad Request",
        code: "VALIDATION_FAILED",
        message: "Request validation failed",
        path: "/api/v1/tickets",
        details: [{ field: "title", rejectedValue: "A title", message: "must be unique" }],
      }),
    );
    renderCreateForm();

    await fillValidValues();
    await userEvent.click(screen.getByRole("button", { name: /create ticket/i }));

    expect(await screen.findByText("must be unique")).toBeInTheDocument();
    expect(screen.getByLabelText("Title")).toHaveValue("A title");
  });

  it("renders a single banner when details[] is empty but message is present", async () => {
    vi.mocked(ticketsApi.createTicket).mockRejectedValue(
      new BrowserApiError({
        timestamp: "2026-01-01T00:00:00Z",
        status: 400,
        error: "Bad Request",
        code: "VALIDATION_FAILED",
        message: "Something specific went wrong",
        path: "/api/v1/tickets",
        details: [],
      }),
    );
    renderCreateForm();

    await fillValidValues();
    await userEvent.click(screen.getByRole("button", { name: /create ticket/i }));

    expect(await screen.findByText("Something specific went wrong")).toBeInTheDocument();
  });

  it("edit mode: submits a PATCH body containing only the fields the user actually changed", async () => {
    const ticket = makeTicket();
    vi.mocked(ticketsApi.updateTicket).mockResolvedValue({ ...ticket, title: "Updated title" });

    renderEditForm(ticket);

    const titleInput = screen.getByLabelText("Title");
    await userEvent.clear(titleInput);
    await userEvent.type(titleInput, "Updated title");

    await userEvent.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() =>
      expect(ticketsApi.updateTicket).toHaveBeenCalledWith(ticket.id, { title: "Updated title" }),
    );
  });

  it("edit mode: shows field-level errors inline on a 400 and keeps the user's typed values", async () => {
    const ticket = makeTicket();
    vi.mocked(ticketsApi.updateTicket).mockRejectedValue(
      new BrowserApiError({
        timestamp: "2026-01-01T00:00:00Z",
        status: 400,
        error: "Bad Request",
        code: "VALIDATION_FAILED",
        message: "Request validation failed",
        path: "/api/v1/tickets/t1",
        details: [{ field: "assignee", rejectedValue: "", message: "must not be blank" }],
      }),
    );

    renderEditForm(ticket);

    const assigneeInput = screen.getByLabelText("Assignee");
    await userEvent.clear(assigneeInput);

    await userEvent.click(screen.getByRole("button", { name: /save changes/i }));

    expect(await screen.findByText("must not be blank")).toBeInTheDocument();
    expect(screen.getByLabelText("Title")).toHaveValue(ticket.title);
  });
});
