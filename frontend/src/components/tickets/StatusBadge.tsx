import type { TicketStatus } from "@/types/ticket";

interface StatusBadgeProps {
  status: TicketStatus;
}

const STATUS_CLASS: Record<TicketStatus, string> = {
  OPEN: "statusOpen",
  IN_PROGRESS: "statusInProgress",
  RESOLVED: "statusResolved",
  CLOSED: "statusClosed",
  CANCELLED: "statusCancelled",
};

/** Renders a ticket's current status. */
export function StatusBadge({ status }: StatusBadgeProps) {
  return (
    <span data-testid="status-badge" className={`statusBadge ${STATUS_CLASS[status]}`}>
      {status}
    </span>
  );
}
