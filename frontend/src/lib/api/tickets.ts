import { browserFetch } from "./browserFetch";
import type { Ticket, TicketStatus, Priority } from "@/types/ticket";
import type { TicketPage } from "@/types/page";

export interface TicketCreateInput {
  title: string;
  description: string;
  priority: Priority;
  assignee: string;
  category?: string | null;
}

export type TicketUpdateInput = Partial<TicketCreateInput>;

export interface ListTicketsParams {
  q?: string;
  status?: TicketStatus;
  page?: number;
  size?: number;
}

function buildQuery(params: ListTicketsParams): string {
  const search = new URLSearchParams();
  if (params.q) search.set("q", params.q);
  if (params.status) search.set("status", params.status);
  if (params.page !== undefined) search.set("page", String(params.page));
  if (params.size !== undefined) search.set("size", String(params.size));
  const query = search.toString();
  return query ? `?${query}` : "";
}

export function listTickets(params: ListTicketsParams = {}): Promise<TicketPage<Ticket>> {
  return browserFetch<TicketPage<Ticket>>(`/api/tickets${buildQuery(params)}`);
}

export function getTicket(id: string): Promise<Ticket> {
  return browserFetch<Ticket>(`/api/tickets/${id}`);
}

export function createTicket(input: TicketCreateInput): Promise<Ticket> {
  return browserFetch<Ticket>("/api/tickets", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function updateTicket(id: string, input: TicketUpdateInput): Promise<Ticket> {
  return browserFetch<Ticket>(`/api/tickets/${id}`, {
    method: "PATCH",
    body: JSON.stringify(input),
  });
}

export function transitionTicket(id: string, targetStatus: TicketStatus): Promise<Ticket> {
  return browserFetch<Ticket>(`/api/tickets/${id}/transitions`, {
    method: "POST",
    body: JSON.stringify({ targetStatus }),
  });
}
