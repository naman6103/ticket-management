"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { getTicket } from "@/lib/api/tickets";
import { TicketDetail } from "@/components/tickets/TicketDetail";
import { ErrorDisplay } from "@/components/errors/ErrorDisplay";
import { BrowserApiError } from "@/lib/api/browserFetch";
import { mapApiError } from "@/lib/errors/mapApiError";
import type { Ticket } from "@/types/ticket";

interface TicketDetailPageProps {
  params: { id: string };
}

export default function TicketDetailPage({ params }: TicketDetailPageProps) {
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useQuery({
    queryKey: ["ticket", params.id],
    queryFn: () => getTicket(params.id),
    retry: false,
  });

  if (isLoading) {
    return <p>Loading ticket…</p>;
  }

  if (error) {
    const payload =
      error instanceof BrowserApiError
        ? error.payload
        : {
            timestamp: new Date().toISOString(),
            status: 0,
            error: "Error",
            code: "NETWORK_ERROR" as const,
            message: "Couldn't reach the server. Please retry.",
            path: `/api/tickets/${params.id}`,
            details: [],
          };
    return <ErrorDisplay messages={mapApiError(payload)} />;
  }

  if (!data) {
    return null;
  }

  return (
    <TicketDetail
      ticket={data}
      onUpdated={(updated: Ticket) => queryClient.setQueryData(["ticket", params.id], updated)}
    />
  );
}
