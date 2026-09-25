import type { TicketStatus } from "@/types/ticket";

/**
 * Client-side mirror of the backend's ticket state machine
 * (specs/001-ticket-management-api/state-machine.md), used only to decide
 * which transition buttons to *offer* (FR-010). Advisory/display-only —
 * the backend's response on submission remains authoritative (FR-013);
 * this table can go stale relative to the backend without causing
 * incorrect behavior, only a possible extra round-trip rejection.
 */
export const NEXT_STATUSES: Record<TicketStatus, TicketStatus[]> = {
  OPEN: ["IN_PROGRESS", "CANCELLED"],
  IN_PROGRESS: ["RESOLVED", "CANCELLED"],
  RESOLVED: ["CLOSED"],
  CLOSED: [],
  CANCELLED: [],
};
