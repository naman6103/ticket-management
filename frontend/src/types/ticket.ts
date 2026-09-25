import type { Comment } from "./comment";

export type Priority = "LOW" | "MEDIUM" | "HIGH";

export type TicketStatus = "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED" | "CANCELLED";

export interface Ticket {
  id: string;
  title: string;
  description: string;
  priority: Priority;
  assignee: string;
  status: TicketStatus;
  category: string | null;
  createdAt: string;
  updatedAt: string;
  /** Present only on GET /tickets/{id}; omitted (not present) on list/search responses. */
  comments?: Comment[];
}
