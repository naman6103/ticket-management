import Link from "next/link";
import type { Ticket } from "@/types/ticket";
import { StatusBadge } from "./StatusBadge";

interface TicketRowProps {
  ticket: Ticket;
}

/** Renders one ticket's summary fields for the list view (FR-001). */
export function TicketRow({ ticket }: TicketRowProps) {
  return (
    <li className="ticketRow">
      <Link href={`/tickets/${ticket.id}`}>
        <span data-testid="ticket-row-title" className="ticketRowTitle">
          {ticket.title}
        </span>
        <StatusBadge status={ticket.status} />
        <span data-testid="ticket-row-priority" className="ticketRowMeta">
          {ticket.priority}
        </span>
        <span data-testid="ticket-row-assignee" className="ticketRowMeta">
          {ticket.assignee}
        </span>
      </Link>
    </li>
  );
}
